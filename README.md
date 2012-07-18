SoftwareFactory
===============
Groovy Utilities to integrate Maven, Jenkins, SCM, Sonar into a Software Factory.

These scripts and classes intend to minimalize the operation to integrate a project into the lifecycle.

Purpose
-------
This code is not production ready.
It's given only as an example of how to control Jenkins, modify pom.xml, report a top 50 of the worst C++ project ...

Main Functionalities
--------------------
- Create a new project : import into SCM, create the CI Job, register into a BOM
- Import a new project : when the project is already imported into the SCM
- Release a project : to fix a version of a stable project
- Delete a project : tag the project as obsoleted

CI Functionalities
------------------
- Create/Update the Release jobs : those which compile the release branch
- Create/Update the Report jobs : those which do the Continuous Inspection

Monitoring 
----------
- Impact Report : From a root job scan the downstream jobs' console to find out the regression errors.
- Last failed report : From a list of job, find out the errors that cause a failed job

SCM 
---
- Move scm repository : move the head from one repository to another (can be also the original)

