# Rheem Platform "GraphChi"

`rheem-graphchi` is the repository, here you can found the implementation of the operators which allow the execution of 
platform "GraphChi"

## Enviroment definition

With scala 2.11
```bash
SCALA_VERSION=scala-11
```
With scala 2.12
```bash
SCALA_VERSION=scala-12
```

### How to compile
```bash
mvn clean compile -P antlr,scala,${SCALA_VERSION}
```

### How to create the package

```bash
mvn clean package -P antlr,scala,${SCALA_VERSION}
```

### How to deploy

```bash
mvn clean deploy -P central,antlr,scala,${SCALA_VERSION}
```
