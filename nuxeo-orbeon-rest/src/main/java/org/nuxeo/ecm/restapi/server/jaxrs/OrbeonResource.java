package org.nuxeo.ecm.restapi.server.jaxrs;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.ecm.core.api.impl.blob.AbstractBlob;
import org.nuxeo.ecm.core.api.impl.blob.ByteArrayBlob;
import org.nuxeo.ecm.core.repository.RepositoryService;
import org.nuxeo.ecm.webengine.model.Template;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.impl.DefaultObject;
import org.nuxeo.runtime.api.Framework;

@WebObject(type = "orbeon")
@Produces(MediaType.APPLICATION_XML)
public class OrbeonResource extends DefaultObject {

    static final String FORM_XML = "data.xml";

    protected static final Log log = LogFactory.getLog(OrbeonResource.class);

    @GET
    @Path("crud/{app}/{form}/{state}/{id}/{file}")
    public Object getFormData(@PathParam("app") String app, @PathParam("form") String form,
            @PathParam("state") String state, @PathParam("id") String id, @PathParam("file") String file) {

        // XXX Hack to bypass security
        class UnrestrictedSessionFetcher extends UnrestrictedSessionRunner {

            protected Blob orbeonData = null;

            public UnrestrictedSessionFetcher(String repository) {
                super(repository);
            }

            @Override
            public void run() {
                DocumentModel formDoc = getOrCreateFormDoc(session, app, form, id, false);
                if (formDoc == null) {
                    return;
                }
                orbeonData = mapNuxeoDocumentToOrbeonData(formDoc, id, file);
                session.save();
            }
        }
        UnrestrictedSessionFetcher fetcher = new UnrestrictedSessionFetcher(getRepositoryName());
        fetcher.runUnrestricted();

        if (fetcher.orbeonData == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        try {
            return Response.status(Status.OK)
                           .type(fetcher.orbeonData.getMimeType())
                           .entity(fetcher.orbeonData.getStream())
                           .build();
        } catch (IOException e) {
            log.error("Error retreving document", e);
            return Response.serverError().build();
        }

    }

    @PUT
    @Path("crud/{app}/{form}/{state}/{id}/{file}")
    public Response createUpdateFormData(@PathParam("app") String app, @PathParam("form") String form,
            @PathParam("state") String state, @PathParam("id") String id, @PathParam("file") String file)
            throws Exception {

        // XXX Hack to bypass security
        new UnrestrictedSessionRunner(getRepositoryName()) {
            @Override
            public void run() {
                DocumentModel formDoc = getOrCreateFormDoc(session, app, form, id, true);

                ByteArrayBlob orbeonData = readBody();
                log.debug("Storing file: " + file + " on form: " + formDoc);
                mapOrbeonDataToNuxeoDocument(formDoc, orbeonData, id, file);
                session.save();
            }
        }.runUnrestricted();

        return Response.status(Status.CREATED).build();
    }

    @DELETE
    @Path("crud/{app}/{form}/{state}/{id}/{file}")
    public Response deleteFormData(@PathParam("app") String app, @PathParam("form") String form,
            @PathParam("state") String state, @PathParam("id") String id, @PathParam("file") String file)
            throws Exception {

        final AtomicBoolean deleted = new AtomicBoolean(false);

        // XXX Hack to bypass security
        new UnrestrictedSessionRunner(getRepositoryName()) {
            @Override
            public void run() {
                DocumentModel formDoc = getOrCreateFormDoc(session, app, form, id, false);
                if (formDoc == null) {
                    return;
                }

                if (FORM_XML.equals(file)) {
                    // Remove all data - form is being deleted
                    session.removeDocument(formDoc.getRef());
                    deleted.set(true);
                } else {
                    // Remove attachment, if found
                    boolean removed = removeAttachment(formDoc, id, file);
                    deleted.set(removed);
                }

                session.save();
            }
        }.runUnrestricted();

        if (deleted.get()) {
            return Response.status(Status.OK).build();
        } else {
            return Response.status(Status.NOT_FOUND).build();
        }
    }

    @POST
    @Path("search/{app}/{form}")
    public Object searchForm(@PathParam("app") String app, @PathParam("form") String form) throws IOException {

        ByteArrayBlob orbeonXML = readBody();

        System.out.println(orbeonXML);

        // XXX parse the search fields

        // XXX Hack to bypass security
        class UnrestrictedSessionSearch extends UnrestrictedSessionRunner {

            protected DocumentModelList forms;

            public UnrestrictedSessionSearch(String repository) {
                super(repository);
            }

            @Override
            public void run() {

                forms = session.query(
                        "select * from OrbeonForm where ob:app='" + app + "' and ob:formName='" + form + "'");
                for (DocumentModel doc : forms) {
                    doc.detach(true);
                }
            }
        }
        UnrestrictedSessionSearch fetcher = new UnrestrictedSessionSearch(getRepositoryName());
        fetcher.runUnrestricted();
        Template template = new Template(ctx, "obsearch2.ftl");
        Map<String, Object> args = new HashMap<>();
        args.put("forms", fetcher.forms);
        args.put("app", app);

        template.args(args);

        return template;
    }

    protected ByteArrayBlob readBody() {
        try (InputStream stream = ctx.getRequest().getInputStream()) {
            byte[] body = IOUtils.toByteArray(stream);
            return new ByteArrayBlob(body);
        } catch (Exception e) {
            log.error("Unable to read payload", e);
            throw new RuntimeException(e);
        }
    }

    protected String getRepositoryName() {
        // yurk!
        return Framework.getService(RepositoryService.class).getRepositoryNames().get(0);
    }

    protected DocumentModel mapOrbeonDataToNuxeoDocument(DocumentModel formDoc, AbstractBlob blob, String id,
            String filename) {
        blob.setFilename(filename);
        if (FORM_XML.equals(filename)) {
            blob.setMimeType("application/xml");
            formDoc.setPropertyValue("file:content", blob);
            return formDoc.getCoreSession().saveDocument(formDoc);
        } else {
            blob.setMimeType("application/octet-stream");
            try {
                DocumentModel child = getOrCreateFormAttachment(formDoc, id, filename, true);
                child.setPropertyValue("file:content", blob);
                return formDoc.getCoreSession().saveDocument(child);
            } catch (Exception ex) {
                log.error("Error saving attachment", ex);
                return null;
            }
        }
    }

    protected boolean removeAttachment(DocumentModel formDoc, String id, String filename) {
        DocumentModel child = getOrCreateFormAttachment(formDoc, id, filename, false);
        if (child != null) {
            formDoc.getCoreSession().removeDocument(child.getRef());
            return true;
        }
        return false;
    }

    protected Blob mapNuxeoDocumentToOrbeonData(DocumentModel formDoc, String id, String filename) {
        Blob blob = null;
        if (FORM_XML.equals(filename)) {
            blob = (Blob) formDoc.getPropertyValue("file:content");
        } else {
            DocumentModel child = getOrCreateFormAttachment(formDoc, id, filename, false);
            if (child != null) {
                blob = (Blob) child.getPropertyValue("file:content");
            }
        }
        return blob;
    }

    protected DocumentModel getOrCreateAppRoot(CoreSession session, String app, boolean create) {
        DocumentRef ref = new PathRef("/" + app);
        if (!session.exists(ref)) {
            if (create) {
                DocumentModel doc = session.createDocumentModel("/", app, "Workspace");
                doc.setPropertyValue("dc:title", app);
                doc.addFacet("orbeon");
                doc.setPropertyValue("ob:app", app);
                session.createDocument(doc);
                session.save();
            } else {
                return null;
            }
        }
        return session.getDocument(ref);
    }

    protected DocumentModel getOrCreateFormRoot(CoreSession session, String app, String form, boolean create) {

        DocumentModel appDoc = getOrCreateAppRoot(session, app, create);
        if (appDoc == null) {
            return null;
        }

        DocumentRef ref = new PathRef(appDoc.getPathAsString() + "/" + form);
        if (!session.exists(ref)) {
            if (create) {
                DocumentModel doc = session.createDocumentModel(appDoc.getPathAsString(), form, "Folder");
                doc.setPropertyValue("dc:title", form);
                doc.addFacet("orbeon");
                doc.setPropertyValue("ob:app", app);
                doc.setPropertyValue("ob:formName", form);
                session.createDocument(doc);
                session.save();
            } else {
                return null;
            }
        }

        return session.getDocument(ref);
    }

    protected DocumentModel getOrCreateFormDoc(CoreSession session, String app, String form, String id,
            boolean create) {

        DocumentModel formFolder = getOrCreateFormRoot(session, app, form, create);
        if (formFolder == null) {
            return null;
        }

        DocumentRef ref = new PathRef(formFolder.getPathAsString() + "/" + id);
        if (!session.exists(ref)) {
            if (create) {
                DocumentModel doc = session.createDocumentModel(formFolder.getPathAsString(), id, "OrbeonForm");
                doc.setPropertyValue("dc:title", "Orbeon Form: " + id);
                doc.setPropertyValue("ob:app", app);
                doc.setPropertyValue("ob:formName", form);
                doc.setPropertyValue("ob:formId", id);
                session.createDocument(doc);
                session.save();
            } else {
                return null;
            }
        }

        return session.getDocument(ref);
    }

    protected DocumentModel getOrCreateFormAttachment(DocumentModel formFolder, String id, String filename,
            boolean create) {

        CoreSession session = formFolder.getCoreSession();
        DocumentRef ref = new PathRef(formFolder.getPathAsString() + "/" + filename);
        if (!session.exists(ref)) {
            if (create) {
                DocumentModel doc = session.createDocumentModel(formFolder.getPathAsString(), filename, "File");
                doc.setPropertyValue("dc:title", filename);
                session.createDocument(doc);
                session.save();
            } else {
                return null;
            }
        }

        return session.getDocument(ref);
    }

}
