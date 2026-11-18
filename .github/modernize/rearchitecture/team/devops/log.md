## [t7] Remediated Maven CVEs
- Dependency graph inspection showed Jackson 3.1.5 and Tomcat 11.0.24 were managed through the Spring Boot parent; the `jackson-bom.version` and `tomcat.version` properties can apply coherent targeted updates.
- The scan reported 11 CVEs in Jackson databind/core and embedded Tomcat; upgrading to Jackson 3.1.8 and Tomcat 11.0.27 cleared the full rescan without changing Spring Boot 4.1.1.
- The first scan input had a typo for JUnit Platform Commons (`1.13.4` rather than the resolved `6.0.3`); both post-fix full scans used the resolved coordinate and returned zero findings.
- The Maven wrapper uses JDK 25.0.5 from its configured runtime although standalone `java` is JDK 21.0.12; no override was necessary.
- Git operations were unavailable because the project directory is not a Git repository; only `pom.xml` was intentionally edited.
- Learnings consumed: [(none)]
