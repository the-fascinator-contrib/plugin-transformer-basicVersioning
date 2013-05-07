plugin-transformer-basicVersioning
==================================
I was thinking about an extremely basic versioning transformer and came up with this.

It's been (rightly) pointed out that it's copying more than versioning but it might be of interest to some. 

In ReDBox you get something like the snapshot below - where each version_ file is a copy made each time the form is saved.

To be really clear - this is just me having a play. The code is not core to ReDBox, Mint or Fascinator. There's no warranty, guarantee or golfing tee.

To use in ReDBOX:

In order to use it you'll need to clone the code and mvn install it. This will deploy it to your local mvn repo.

In your ReDBox build pom, add
```
<dependency>
	<groupId>com.googlecode.the-fascinator.plugins</groupId>
	<artifactId>plugin-transformer-basicVersioning</artifactId>
	<version>0.0.1-SNAPSHOT</version>
</dependency>
```
In your system-config.json, add this to transformerDefaults:
```
"basicVersioning": {
    "id": "basicVersioning",
    "sourcePayload": ".tfpackage"
}
```
In dataset.json harvest config, configure:
```
"transformer": {
    "curation": ["local"],
    "metadata": ["jsonVelocity", "basicVersioning"]
},
```
