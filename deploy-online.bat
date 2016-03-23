rem set MAVEN_OPTS=-Dfile.encoding=UTF-8
call mvn -U -Dmaven.test.skip=true -DfailIfNoTests=false -P online clean install package

echo ------------------------------


pause