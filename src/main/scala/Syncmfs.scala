package com.synchmfs {


	import com.mongodb._
	import com.mongodb.gridfs._
	import java.io.File

	import com.codahale.logula.Logging
	import org.apache.log4j.Level
	import scopt._
	
	object Syncmfs extends Logging{


	/*

			Syncher works with the way that described below


			At first,
				If root directory is exists moves to synchronization phase,
			 	else, it just fetches all files on remote

			 
			Synchronization phase

			1. [DONE] When first fetched repository from remote, algorithm creates .syncmfs.tree file within directory.
					
					This file includes latest fetched content file names and md5 hashes.

						FILENAME
						MD5

			2. [DONE] (DirectoryValidator, TreeDbValidator) When synchronization request has started it starts to create local synch. list
				
				General synch list has a structure like below..

					OPERATION => 0 - ADD, 1 - UPDATE, 2 - DELETE
					FILENAME


				2.1 (TreeDbValidator) It checks .syncmfs.tree file contents with the current directory tree...
						
						If the file described on .syncmfs.tree is not exists on current directory tree, it is marked as DELETE on local synch list

						If the file described on .syncmfs.tree is exists but not has same MD5 on current tree, it is marked as UPDATE on local synch list
						

				2.2 (DirectoryValidator) It checks current tree 
				
						If the file found on tree has no description on .syncmfs.tree the file will be barked as ADD on local synch list
						
			3. [DONE] (RemoteRepositoryValidator) When local synch. list finished it gets remote file list and starts to generate remote synch. list

				If any file on remote has on file on local it is marked as ADD on remote synch. list

				If any file on remote has different MD5 local it is marked as UPDATE on remote synch. list

				If any file on local has added on local synchmfs.tree but has no value on remote, it is marked as DELETE on remote synch. list

			





		*/


		var rootPaths: Array[String] = null

		

		val utility = Utility

		val settings = Settings



		
		

		def createAndFetchRepository(rootDir: File, database: DB): Boolean = {
			
			var ret = if (!rootDir.exists) rootDir.mkdirs else true;

			if (ret) {
				
				rootPaths = utility.getParentPaths(rootDir)

				var treeDb: TreeDb = null
				try {

					treeDb = new TreeDb(rootDir.getAbsolutePath())

					val gridFs = new GridFS(database, settings.bucket)

					val fileCursor = gridFs.getFileList

					while (fileCursor.hasNext) {
						
						val _fileOnRemote = fileCursor.next.asInstanceOf[GridFSDBFile]

						val c_fileName = _fileOnRemote.getFilename

						val fileOnRemote = gridFs.findOne( c_fileName )

						val realPaths = utility.convertToRealPaths(rootDir,c_fileName)

						var absoluteDirPath = realPaths(0)

						val absoluteFilePath = realPaths(1)

						val f_absoluteDir = new File(absoluteDirPath)

						if (!f_absoluteDir.exists)
							ret = f_absoluteDir.mkdirs


						if (ret) {
							
							val fToCreate = new File(absoluteFilePath)

							var fetch = false

							if (fToCreate.exists) {
								val cMd5 = utility.getMD5(fToCreate)

								fetch = !(cMd5 == fileOnRemote.getMD5) 
								if (!fetch) {
									log.info("%s is already exists.. no need to fetch",absoluteFilePath)
								}
									

								if (fetch) { 
									log.info("Updating %s with the latest repository version",absoluteFilePath)
									fToCreate.delete
								}

							} else {
								fetch = true
							}



							if (fetch) {

								println("Receiving file  "+absoluteFilePath)

								// Receiving file to target path
								fileOnRemote.writeTo(absoluteFilePath)

								//printf("MD5 of file: %s is %s - on remote MD5: %s\n",absoluteFilePath,utility.getMD5(new File(absoluteFilePath)), fileOnRemote.getMD5)
							}

							treeDb.addFile(c_fileName, fileOnRemote.getMD5)

						}

					}

					println("All repository fetched successfully")

					
					
				} catch {
					case e: Exception => {
						// Nothing should be done.. it already returns false
						log.fatal(e,"Repository fetch failed.")
					}
				} 
				
				if (treeDb != null)
						treeDb.close
				


			}



			return ret;
		}


		def remoteStatus(rootDir:File, db:DB): SynchList = {
			

			val treeDb = new TreeDb(rootDir.getAbsolutePath)

			val remoteSynchList = new SynchList
			
			val remoteRepositoryValidator = new RemoteRepositoryValidator(treeDb,rootDir,db)

			remoteRepositoryValidator.updateSynchList(remoteSynchList)

			treeDb.close

			return remoteSynchList

		}

		def localStatus(rootDir:File): SynchList = {
			
			val treeDb = new TreeDb(rootDir.getAbsolutePath)

			val treeDbValidator = new TreeDbValidator(treeDb,rootDir)
			val directoryValidator = new DirectoryValidator(treeDb,rootDir)

			val localSynchList = new SynchList

			treeDbValidator.updateSynchList(localSynchList)
			directoryValidator.updateSynchList(localSynchList)

			treeDb.close

			return localSynchList

		}

		def update(rootDir:File, db:DB) {

			val remoteSynchList = remoteStatus(rootDir,db)

			val treeDb = new TreeDb(rootDir.getAbsolutePath)

			// println("REMOTE SYNCH LIST")

			// remoteSynchList.printOut

			val synchListProcessor = new SynchListProcessor(treeDb, db, rootDir)

			if (synchListProcessor.updateSynchList(remoteSynchList)) {
				
				log.info("Successfully updated remote changes")
			}

			treeDb.close
		}

		def commit(rootDir:File, db:DB) {
			
			val localSynchList = localStatus(rootDir)

			val treeDb = new TreeDb(rootDir.getAbsolutePath)

			// println("LOCAL SYNCH LIST")

			// localSynchList.printOut

			val synchListProcessor = new SynchListProcessor(treeDb, db, rootDir)

			if (synchListProcessor.commitSynchList(localSynchList)) {
				
				log.info("Successfully committed local changes")
			}

			treeDb.close
		}

		def main(args: Array[String]) { 

			println("SyncMFS: MongoDB-GridFS directory synchronizer by Harun ESUR")
		
			val ACTION_COMMIT = 0
			val ACTION_UPDATE = 1
			val ACTION_SYNCH = 2
			val ACTION_WATCH = 3
			val ACTION_STATUS = 4
			val ACTION_RSTATUS = 5

			val ACTION_DESC: String = """One of the acceptable values within: 
	COMMIT  - Commits current tree to remote,
	UPDATE  - Updates remote changes with local tree, 
	SYNCH   - Synchronizes changes with remote and local tree,
	WATCH   - Starts watching current tree and remote repository for changes and updates whenever a change has been made
	STATUS  - Shows local changes since last update
	RSTATUS - Shows remote changes since last update"""

			var ACTION = -1

			 val parser = new OptionParser("syncmfs") {
			 	arg("<action>", ACTION_DESC, {v: String => 
				 	if (v.toUpperCase.trim == "COMMIT") ACTION = ACTION_COMMIT else
				 	if (v.toUpperCase.trim == "UPDATE") ACTION = ACTION_UPDATE else 
				 	if (v.toUpperCase.trim == "SYNCH") ACTION = ACTION_SYNCH else 
				 	if (v.toUpperCase.trim == "WATCH") ACTION = ACTION_WATCH else 
				 	if (v.toUpperCase.trim == "STATUS") ACTION = ACTION_STATUS else 
				 	if (v.toUpperCase.trim == "RSTATUS") ACTION = ACTION_RSTATUS else ACTION = -1  })
			 	opt("s","settings","<file>","Location of .syncmfs file\n\tNOTE: Command-line parameters overrides settings given in settings file", {v: String => settings.settingsFileLocation = v})
			 	opt("h","host","<host name or ip>","Host server running MongoDB", {v: String => settings.host = v})
			 	opt("b","bucket","<bucket name>","Bucket name on GridFS", {v: String => settings.bucket = v})
			 	opt("d","db","<database name>","Database name on MongoDB", {v: String => settings.db = v})
			 	opt("u","user","<user name>","User name for MongoDB", {v: String => settings.user = v})
			 	opt("r","root","<directory path>","Root directory path for repository", {v: String => settings.dir = v})
			 	booleanOpt("l","log","Enable logging", {v: Boolean => if (v) settings.logging = "INFO" })
			 	booleanOpt("f","flog","Enable logging to file (Should be used with -l parameter)", {v: Boolean => if (v) settings.fileLogging = "INFO" })
			 	booleanOpt("o","overwrite","Discard local changes when updating", {v: Boolean => if (v) settings.overwriteLocalChanges = v })
			   
			  }


			if (!parser.parse(args)) {
			
					sys.exit(0)
			}

			if (settings.settingsFileLocation != "")
				settings.processSettingsFile

			else {


				
				if (settings.host == null) {
				 	println("Please add host information to options with -h option")
				 	sys.exit(0)
				 }

				 if (settings.db == null) {
				 	println("Please add db information to options with -d option")
				 	sys.exit(0)
				 }

				 if (settings.dir == null) {
				 	println("Please add directory root information to options with -r option")
				 	sys.exit(0)
				 }

				 val fDirectory = new File(settings.dir)
				 if ((fDirectory.exists && !fDirectory.isDirectory()) || !fDirectory.exists)
				 {
				 	println("There is no DIRECTORY with "+settings.dir+" path"); 
				 	sys.exit(0)
				 }
			}

			
			try {

				val mongo = new Mongo(settings.host)
				val mongoDb = mongo.getDB(settings.db)

				if (settings.user != null)
					if (!mongoDb.authenticate(settings.user,settings.pass.toArray)) {
						log.error("Authentication failed!")
						sys.exit(0)
					}

				// println("Connected to "+settings.host+" / "+settings.db+" with driver v"+mongo.getVersion())

				val rootDir = new File(settings.dir)

				

				if (!new File(rootDir+File.separator+Settings.TREE_DB_DIR+File.separator+Settings.DB_FILENAME).exists) {


					if (ACTION == ACTION_UPDATE || ACTION == ACTION_SYNCH || ACTION == ACTION_WATCH) {

						log.info("Fetching remote repository..")

						createAndFetchRepository(rootDir, mongoDb)

						if (ACTION != ACTION_WATCH) {
							sys.exit(0)
						}
					}
					else {
						log.warn("Cannot commit %s because there is no syncmfs repository",settings.dir)
						sys.exit(0)
					}

				}
				
			

				if (ACTION == ACTION_COMMIT) {
					
					commit(rootDir,mongoDb)
				}

				 else if (ACTION == ACTION_UPDATE) {

					
					update(rootDir, mongoDb)
					
				} else if (ACTION == ACTION_SYNCH) {
					
					update(rootDir,mongoDb)

					commit(rootDir,mongoDb)
				} else if (ACTION == ACTION_STATUS) {
					
					localStatus(rootDir).printOut

				} else if (ACTION == ACTION_RSTATUS) {
					
					

					remoteStatus(rootDir,mongoDb).printOut

				} else if (ACTION == ACTION_WATCH) {

					log.error("Watching system is not implemented yet..")
				}
				
			} catch {
				case e: Exception => {
					//println("Exception occured while trying to connect..")
					log.fatal(e,"Exception occured while running Syncmfs")
					//e.printStackTrace
				}
			}

			log.info("SyncMFS is exiting")

			sys.exit(0)

			
		}

	}
}