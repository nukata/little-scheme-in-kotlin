run: scm.jar
	java -jar scm.jar

scm.jar: scm.kt scm_j.kt
	kotlinc -include-runtime -d scm.jar scm.kt scm_j.kt

distclean:
	rm -f scm.jar *~
