## StateGraph

Some implementation ideas can be found under domain/readme.md

## SBT

### Run
`sbt run`

### Run functional tests
`sbt run`

### Run unit tests (domain)
`sbt "project domain" run`

## Docker

####Build
`docker build -t aidar/stagegraph:1.4 .`

####Run
`docker run --rm -it -p 8080:9000 aidar/stagegraph:1.4 sbt run`

####Run functional tests
`docker run --rm -it aidar/stagegraph:1.4 sbt test`

####Run unit tests (domain)
`docker run --rm -it aidar/stagegraph:1.4 sbt "project domain" run`