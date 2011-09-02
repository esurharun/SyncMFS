package com.synchmfs {

	import java.sql._
	import java.io.File
	import com.mongodb._
	import com.mongodb.gridfs._

	import com.codahale.logula.Logging
	import org.apache.log4j.Level

	class SynchListProcessor(treeDb:TreeDb, remoteDb:DB, rootDir:File) extends Logging {


		val utility = Utility


		def addOrUpdateFileOnTreeDb(fileName:String,md5:String) {
			if (treeDb.isFileExists(fileName)) {
				
				treeDb.removeFile(fileName)
			}

			treeDb.addFile(fileName,md5)
		}

		// Operations from LOCAL -> REMOTE

		def updateRemoteRepository(fileName:String) {

			deleteOnRemoteRepository(fileName)

			val gridFs = new GridFS(remoteDb, Settings.bucket)

			val gfsIn = gridFs.createFile( new File(utility.convertToRealPaths(rootDir,fileName)(1)) );      
			
			gfsIn.setFilename(fileName)
			 
			log.info("Uploading %s ...",fileName)
    		
    		gfsIn.save();

    		addOrUpdateFileOnTreeDb(fileName,getMd5OnRemote(fileName))
			
		}

		def addToRemoteRepository(fileName:String) {
			
			log.info("Adding to remote repository: %s",fileName)

			updateRemoteRepository(fileName)

		}

		def deleteOnRemoteRepository(fileName:String) {
			
			val gridFs = new GridFS(remoteDb, Settings.bucket)

			gridFs.remove( fileName )

			
			treeDb.removeFile(fileName)
		}


		// Operations from REMOTE -> LOCAL

		def updateLocalFile(fileName:String) {
		
			val gridFs = new GridFS(remoteDb, Settings.bucket)
		
			val fileOnRemote = gridFs.findOne( fileName )

			val realPaths = utility.convertToRealPaths(rootDir,fileName)

			var absoluteDirPath = realPaths(0)

			val absoluteFilePath = realPaths(1)

			val f_absoluteDir = new File(absoluteDirPath)

			if (!f_absoluteDir.exists)
				f_absoluteDir.mkdirs

			val fToCreate = new File(absoluteFilePath)

			if (fToCreate.exists) {
					
					fToCreate.delete

			} 
			
			log.info("Fetching file: %s"+absoluteFilePath)

			// Receiving file to target path
			fileOnRemote.writeTo(absoluteFilePath)


			addOrUpdateFileOnTreeDb(fileName, fileOnRemote.getMD5)	
		}

		def downloadRemoteFile(fileName:String) {
			
			log.info("Updating local file: %s",fileName)
			updateLocalFile(fileName)
		}

		def deleteLocalFile(fileName:String) {

			log.info("Deleting local file: %s",fileName)
			
			val realPaths = utility.convertToRealPaths(rootDir,fileName)

			val absoluteFilePath = realPaths(1)

			new File(absoluteFilePath).delete

			treeDb.removeFile(fileName)

		}

		def getMd5OnRemote(fileName: String): String = {


			log.trace("getMd5OnRemote: %s",utility.shortenFilenameFromBeginning(fileName))

			val gridFs = new GridFS(remoteDb, Settings.bucket)

			val fileOnRemote = gridFs.findOne( fileName )

			if (fileOnRemote == null)
				return null

			return fileOnRemote.getMD5

		}

		def getMd5OnLocal(fileName: String): String  = {
			

			log.trace("getMd5OnLocal: %s",utility.shortenFilenameFromBeginning(fileName))

			val rFileName = new File(utility.convertToRealPaths(rootDir,fileName)(1))

			return utility.getMD5(rFileName)

		}

		def getMd5OnTreeDb(fileName: String): String = {

			log.trace("getMd5OnTreeDb: %s",utility.shortenFilenameFromBeginning(fileName))
			treeDb.md5For(fileName);	
		} 



		def commitSynchList(localSynchList:SynchList): Boolean = {
			
			log.debug("Starting to check committing files..")

			localSynchList.resetOffset

			var lSynchItem = localSynchList.getNext

			while (lSynchItem != null) {
				
				var opCode = lSynchItem(0).toInt
				var fileName = lSynchItem(1)

				log.trace("Checking file %s",utility.shortenFilenameFromBeginning(fileName))

				if (opCode == localSynchList.OPERATION_UPDATE) {

					// We have to be sure whether MD5 on tree db is equals with remote

					if (getMd5OnRemote(fileName) != getMd5OnTreeDb(fileName)) {

						log.warn("There is a conflict for %s . File on repository is not same as last time we received", fileName)

						return false
						
					}
					
				} else if (opCode == localSynchList.OPERATION_ADD) {

					// We have to be sure there is no file on remote with that name

					if (getMd5OnRemote(fileName) != null) {
						
						log.warn("There is already a file named %s on remote repository",fileName)

						return false
					}
					
				} else if (opCode == localSynchList.OPERATION_DELETE) {
					

					// We have to be sure whether MD5 on tree db is equals with remote

					if (getMd5OnRemote(fileName) != getMd5OnTreeDb(fileName)) {

						log.warn("There is a conflict for %s . File on repository is not same as last time we received", fileName)

						return false
						
					}

				}





				lSynchItem = localSynchList.getNext
			}


			log.debug("Starting to commit files..")

			localSynchList.resetOffset

			lSynchItem = localSynchList.getNext
			while (lSynchItem != null) {


				var opCode = lSynchItem(0).toInt
				var fileName = lSynchItem(1)

			
				if (opCode == localSynchList.OPERATION_UPDATE) {

					updateRemoteRepository(fileName)
					
				} else if (opCode == localSynchList.OPERATION_ADD) {

					addToRemoteRepository(fileName)
					
				} else if (opCode == localSynchList.OPERATION_DELETE) {
					
					deleteOnRemoteRepository(fileName)

				}


				lSynchItem = localSynchList.getNext
				
			}

			return true


		}

		def updateSynchList(remoteSynchList:SynchList) : Boolean = {
			
			log.debug("Starting to check update files..")


			remoteSynchList.resetOffset

			var rSynchItem = remoteSynchList.getNext

			while (rSynchItem != null) {
				
				var opCode = rSynchItem(0).toInt
				var fileName = rSynchItem(1)

				if (opCode == remoteSynchList.OPERATION_UPDATE) {

					// We have to be sure local file is not changed with last fetch

					if (!Settings.overwriteLocalChanges && getMd5OnTreeDb(fileName) != getMd5OnLocal(fileName))
					{
						
						log.warn("There is a conflict for %s . File has changed since last update", fileName)


						return false
					}



					
				} else if (opCode == remoteSynchList.OPERATION_ADD) {

					// We have to be sure there is no file exists

					if (!Settings.overwriteLocalChanges && getMd5OnLocal(fileName) != null)
					{
						
						log.warn("There is a conflict for %s . File already exists cannot overwrite it", fileName)


						return false
					}
					
				} else if (opCode == remoteSynchList.OPERATION_DELETE) {
					

					// Nothing reasonable to check for now

					if (!Settings.overwriteLocalChanges && getMd5OnTreeDb(fileName) != getMd5OnLocal(fileName))
					{
						
						log.warn("There is a conflict for %s . File has changed since last update. Cannot delete. ", fileName)


						return false
					}

				}



				rSynchItem = remoteSynchList.getNext
			}


			log.debug("Starting to update")

			remoteSynchList.resetOffset

			

			rSynchItem = remoteSynchList.getNext
			while (rSynchItem != null) {


				var opCode = rSynchItem(0).toInt
				var fileName = rSynchItem(1)

			
				if (opCode == remoteSynchList.OPERATION_UPDATE) {

					updateLocalFile(fileName)
					
				} else if (opCode == remoteSynchList.OPERATION_ADD) {

					downloadRemoteFile(fileName)
					
				} else if (opCode == remoteSynchList.OPERATION_DELETE) {
					
					deleteLocalFile(fileName)

				}



				rSynchItem = remoteSynchList.getNext

			}




			return true;
		}


	}
}