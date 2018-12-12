@echo off
FOR /F "tokens=1,2 delims==" %%G IN (environment.properties) DO (set %%G=%%H)
TITLE Kafka Connect

set mypath=%cd%
set kafka-connect-path=%cd%\..

xcopy %kafka-connect-path%\target\kafka-connect-zeebe-*-SNAPSHOT.jar %kafka%\plugins\ /E /I /Y


%kafka%\bin\windows\connect-standalone %kafka%/config/connect-standalone.properties %kafka-connect-path%\src\test\resources\zeebe-test-sink.properties %kafka-connect-path%\src\test\resources\zeebe-test-source.properties
cd %mypath%	