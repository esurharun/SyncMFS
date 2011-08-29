package com.synchmfs {


	import com.codahale.logula.Logging
	import org.apache.log4j.Level
	
	object Settings  extends Logging  {
		
		import java.io.File
		import java.io.FileInputStream
		import java.lang._
		import java.util._

		
		val filename = ".syncmfs"
		val TREE_DB_DIR = ".syncmfs.db"
		val DB_FILENAME = "syncmfs.tree"
		
		val homeDir = System.getProperty("user.home")
		val curDir = new File(".").getCanonicalPath()
		//val posLocs = Array(curDir+File.separator+filename, homeDir+File.separator+filename)
		

		// Settings variables
		var settingsFileLocation = ""
		var host: String = null
		var db: String = null
		var user: String = null
		var pass: String = null
		var dir: String  = null
		var logging: String = null
		var fileLogging: String = null
		var overwriteLocalChanges: Boolean = false
		var bucket: String = "fs"



		import scala.util.control.Breaks._	


		def processSettingsFile {

			var posLocs: Array[String] = Array[String]()

			if (settingsFileLocation != "")
				posLocs = Array[String]( settingsFileLocation, curDir+File.separator+filename, homeDir+File.separator+filename)
			else 
				posLocs = Array[String](  curDir+File.separator+filename, homeDir+File.separator+filename)

			
			breakable {
				posLocs.foreach( loc => {
				
					if (new File(loc).exists()) {
						
				 		//println(loc+" exists")
				 		settingsFileLocation = loc
				 		break
				 	}
				 })
			}
		


		
			 if (settingsFileLocation == "") {
				
			 	println("Cannot find an appropriate settings file. \n Please consider building up a settings file on paths any of below")

			 	println(" => "+settingsFileLocation)
			 	posLocs.foreach( loc => {
			 		println(" => "+loc)
			 	})

			 	sys.exit(0)

			 }


			val sets = new Properties()

			sets.load(new FileInputStream(settingsFileLocation))

			if (host == null)
			host = sets.getProperty("host")
			if (db == null)
			db = sets.getProperty("db")
			if (user == null)
			user = sets.getProperty("user")
			if (pass == null)
			pass = sets.getProperty("pass")
			if (dir == null)
				dir  = sets.getProperty("dir")
			if (bucket == "fs")
				bucket = sets.getProperty("bucket")

			if (bucket == null) {
				bucket = "fs"
			}

			if (logging == null)
			logging = sets.getProperty("logging")
			if (fileLogging == null)
			fileLogging = sets.getProperty("logging.file")

			val tOverwriteLocalChanges = sets.getProperty("overwrite.local.changes")

			if (tOverwriteLocalChanges != null) {
				
				if (tOverwriteLocalChanges.trim.toUpperCase == "TRUE") {
					overwriteLocalChanges = true
				} else
					overwriteLocalChanges = false

			}

			if (host == null) {
			 	println("Please include host definition in "+settingsFileLocation)
			 	sys.exit(0)
			 }

			 if (db == null) {
			 	println("Please include db name in "+settingsFileLocation)
			 	sys.exit(0)
			 }

			 if (dir == null) {
			 	println("Please include directory definition to sync in "+settingsFileLocation)
			 	sys.exit(0)
			 }

			 val fDirectory = new File(dir)
			 if ((fDirectory.exists && !fDirectory.isDirectory()) || !fDirectory.exists)
			 {
			 	println("There is no DIRECTORY with "+dir+" path"); 
			 	sys.exit(0)
			 }

			 if (logging != null) {

			 	val strLogging: String = logging.asInstanceOf[String].toUpperCase
			 	//val loggingLevel = if (logging)
			 	
			 	Logging.configure { log =>
				  	log.registerWithJMX = false

				  	// FATAL < ERROR < WARN < INFO < DEBUG < TRACE

				  	log.level =   if (strLogging == "DEBUG" ) Level.DEBUG
				  				else if (strLogging == "TRACE") Level.TRACE
				  				else if (strLogging == "WARN") Level.WARN
				  				else if (strLogging == "FATAL") Level.FATAL
				  				else if (strLogging == "ERROR") Level.ERROR
				  				else Level.INFO
				  

				  	log.console.enabled = true
				  	log.console.threshold = log.level

					if (fileLogging != null) {

					  log.file.enabled = true
					  log.file.filename = "syncmfs.log"
					  log.file.maxSize = 10 * 1024 // KB
					  log.file.retainedFiles = 5 // keep five old logs around
					  log.file.threshold = log.level
					  //log.file.threshold = 
	  				} 

				}


			 } else {
			 	Logging.configure { log =>
				  log.registerWithJMX = false
				  log.level =  Level.OFF
				  log.console.enabled = false
				  log.file.enabled = false
				  log.syslog.enabled = false
				}

			 }


			log.info("Using settings file at %s",settingsFileLocation)

		}

		//def serverHost 

	}

}
