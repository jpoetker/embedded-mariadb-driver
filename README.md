# embedded-mariadb-driver
JDBC Driver that can start up an embedded MariaDB instance.

This driver will attempt to connect to a running instance of a MySQL or MariaDB database per the JDBC URL. If the database cannot be connected to, the driver will
use [MariaDB4j](https://github.com/vorburger/MariaDB4j) to automatically create an embedded instance of a database in your JVM, and begin connecting to it.

The JDBC URL format support should match that of the MariaDB driver (or MySQL driver), however, starting up an embedded database will only work if the host
for the database is "localhost". If the JDBC URL specifies a different host, and the Driver cannot connect to that host, it will not start an embedded instance.

The driver _will_ start the embedded instance on the port from the JDBC URL, or use the default MySQL port if one is not provided.

## What is the point?
I am using this to facilitate integration testing. I work on projects that use MySQL databases, and I typically have an instance of MySQL running. This instance typically
works fine for running integration tests locally, but we have a CI environment as well, and this environment does not have a locally running MySQL database. While it would be
possible to configure a shared database for the purpose of CI builds, it can get a little difficult to manage if many builds are running, dropping and creating schemas etc.

With this driver, the CI builds will simply start up an instance for the integration tests, create the schema (we use liquibase for this). Run the tests, then when complete 
the database is stopped.


