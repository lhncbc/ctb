# CTB - Custom Taxonomy Builder

## Description

Given a list of terms and a set of UMLS files, the CTB generates a
subset the of UMLS containing the supplied terms and their word-based
variants.

## Inputs

The following files should be placed in the data/input directory:

+ MRCONSO.RRF concepts file
+ MRSTY.RRF concept -> semantic types file

Supplied to Web Interface

+ list of supplied terms

## Outputs

+ Custom version of mrconso.rrf
+ Custom version of mrsty.rrf

## Usage

To use CTB you must first create indexes of your UMLS files and then
start the tool.

### Prepare Knowledge Sources

Copy MRCONSO.RRF, MRSTY.RRF to ctb/data/input/'your data set name'/.

In the ctb directory run:

    bin/prepumls.sh 'your data set name'

For example:

    bin/prepumls.sh 2016AA

### Update the system configuration file

There should be a file called ctb.properties in the "config"
directory.  In ctb.properties change:

    ctb.ivf.dataroot: ...

to:

    ctb.ivf.dataroot: data/ivf/<your data set name>

### Add LVG to configuration file for term expansion

If you want to use the Lexical Tools Lexical Variant Generator (LVG)
to supply term combinations not found in the UMLS then download LVG
from the Lexical Systems Group website
(https://lsg3.nlm.nih.gov/LexSysGroup/Projects/lvg/current/web/index.html)
and install it according to its directions.  After installing the
Lexical Tools then add the following to the ctb.properties file:

    ctb.lvg.directory: {LVGDIR}

Where LVGDIR is the location of your LVG installation.

### Start up system

In the top-level ctb directory run:

    java -jar target/ctb-0.1.0-SNAPSHOT-standalone.jar <port>

or if you have Leiningen:

    lein ring server 

Then point your web browser to localhost:3000 (or if you supplied a
port number, that port number.)

### Supply Term List

Paste your term list into the "Input Terms" (first) page and press
"Submit".

### Filter synonyms

Select or de-select terms in Synonym Set View to filter the synonyms
generated by the tool and press "Submit".

### Generate Data Set

The generated dataset will be placed in the directory
resources/public/output/user<number>/<queryhash>/.

The directory should contain the following files:

    filtered-synset
    filtered-termlist.edn
    mrconso.rrf
    mrsty.rrf
    params
    synonymsp.checksum
    termlist

## For Developers

### Running the system in Apache Tomcat

If you have tomcat you can use the file
target/ctb-0.1.0-SNAPSHOT-standalone.war to deploy the system to
tomcat.

Currently, the application expects the config directory containing
ctb.properties and the data directory containing the indexes to be in
the top-level apache-tomcat directory (where directories conf,
webapps, etc. resides)

Note: CTB has not been extensively tested in Tomcat and may require
modification to work properly.

## License

CTB is product of the U.S. Government and is not subject to copyright.

For more information see:
  http://www.usa.gov/government-works
