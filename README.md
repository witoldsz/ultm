ULTM
===
**Ultra Lightweight** Transaction Manager for JDBC

Introduction:
---
Simple is beautiful.

Brief:
---
ULTM gives you basic transaction API for a JDBC data source. All you have to do is to prepare a `DataSource`, pass it to this library and in exchange it gives you `TxManager` (to control transactions) and a dedicated, *managed* `DataSource` you should use to fetch connections.

ULTM works with with JDBC libraries. All you have to do is to pass the ULTM managed DataSource to them and you are done. Just remember that you should never mix transaction management from different libs. Try commiting or rolling back on connections from ULTM DataSource and you will get `UnsupportedOperationException`.

When to use it?
---
Use it whenever you need JDBC but you do not want to play with transactions manually and you do not want to use complex environments like JavaEE or Spring.
ULTM is nothing but few lines of code with no external dependencies.

It is a perfect candidate when you create a tiny application or when you build complex one from microservices. That was my case, that was where ULTM was born: big complex application built of dozens Java and non-Java services talking to each other by messages over exchanges and queues. **ULTM**+JOOQ+Liquibase and tiny wrapper around RabbitMQ (or equivalent), glued with Guice are my favorites for SQL/Java-based microservices.

Get it:
---
- Maven, Ivy, Gradle, SBT, you name it...
 - http://mvnrepository.com/artifact/com.github.witoldsz/ultm
 - https://bintray.com/witoldsz/maven/ultm/view

- `pom.xml` coordinates
```xml
<dependency>
   <groupId>com.github.witoldsz</groupId>
   <artifactId>ultm</artifactId>
   <version>...</version>
</dependency>
```

Example usage:
--------------

First of all: you have to get the DataSource somehow. It can be a raw DataSource directly from your database vendor or from some database connection pool library. Let's get one from PostgreSQL:

> hint: check the JavaDoc for `PGPoolingDataSource` at http://jdbc.postgresql.org/documentation/publicapi/org/postgresql/ds/PGPoolingDataSource.html

```java
PGPoolingDataSource pgDataSource = new PGPoolingDataSource();
pgDataSource.setPassword(System.getenv("PG_SQL_PASS"));
pgDataSource.setUser(System.getenv("PG_SQL_USER"));
pgDataSource.setServerName(System.getenv("PG_SQL_ADDR"));
pgDataSource.setDatabaseName("demo");

ULTM ultm = new ULTM(pgDataSource);

DataSource ds = ultm.getManagedDataSource();
TxManager txManager = ultm.getTxManager();
```

Now, you are ready to go:

```java
txManager.begin();
try {
  do_something();
  txManager.commit();
} catch (Exception ex) {
  txManager.rollback();
  throw ex; // or whatever you find appropriate
}
```

Same as above, but using *Unit of Work* pattern:

```java
txManager.tx(() -> do_something());
// or when do_something() throws checked exceptions
txManager.txChecked(() -> do_something());
```


Tests are always good source of knowledge, so check them out:
https://github.com/witoldsz/ultm/blob/master/src/test/java/com/github/witoldsz/ultm/test/ULTMTest.java

You are welcome
---
If you have question, suggestion, improvement, fix or the like, please create an issue or pull request. Everyone interested will get notified.
