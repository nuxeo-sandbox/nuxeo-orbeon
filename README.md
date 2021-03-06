# Nuxeo Orbeon

## Goal

The idea is to be able to use [Orbeon](https://github.com/orbeon/orbeon-forms/) form engine and store form data into Nuxeo.

The data binding must be bi-directional:

 - initiallize the form in `form-runner` from data retroeved from the Nuxeo repository
 - save the data from `form-runner` into Nuxeo

The goal if this project is to build a prototype.

## Principles

Orbeon allows to define a [Persistence provider](https://doc.orbeon.com/form-runner/api/persistence/) that can be use:

 - for storing forms data
 - for storing forms configuration

Here, for now, we are targetting the first use case.

The idea is to deploy inside Nuxeo a JAXRS endpoint that will be added to the api endpoint.

## Buidling

        # mvn clean install

## How to deploy 


### Nuxeo side

On a vanilla Nuxeo installation you can run :

        # $NUXEO_HOME/bin/nuxeoCtl mp-install $PROJECT_HOME/marketplace-orbeon/marketplace/target/marketplace-nuxeo-orbeon-*.zip

You can also simply copy the plugin jar inside the `bundles` or `plugins` direction of the Nuxeo server.

### Orbeon side

Orbeon can be deployed side-by-side with Nuxeo: inside the same tomcat server.

You need to enable the Nuxeo persistence provider: see [orbeon-config/properties-form-runner.xml](orbeon-config/properties-form-runner.xml)

## Limitations

### Storage without mapping

There is currently no direct mapping between Orbeon data and the Nuxeo meta-data: all the XML data send by Orbeon is currently stored as an attached XML file.

Next step could be:

 - enable bi-directional mapping
 - re-use the XSD schema fro Orbeon

### Security

I am currently using the `openURL` hack to allow Orbeon to access Nuxeo API without authentication ...

## About Nuxeo
Nuxeo dramatically improves how content-based applications are built, managed and deployed, making customers more agile, innovative and successful. Nuxeo provides a next generation, enterprise ready platform for building traditional and cutting-edge content oriented applications. Combining a powerful application development environment with SaaS-based tools and a modular architecture, the Nuxeo Platform and Products provide clear business value to some of the most recognizable brands including Verizon, Electronic Arts, Netflix, Sharp, FICO, the U.S. Navy, and Boeing. Nuxeo is headquartered in New York and Paris. More information is available at www.nuxeo.com.
