package com.synchmfs {
	

	import java.io.File
	import com.mongodb._
	import com.mongodb.gridfs._

	import com.codahale.logula.Logging
	import org.apache.log4j.Level
	
	class RemoteRepositoryValidator(treeDb: TreeDb, rootDir: File, remoteDb: DB) extends Logging {

		val utility = Utility

		def updateSynchList(synchList: SynchList) {
				

			log.debug("Updating remote synch. list for %s on bucket %s",rootDir.getAbsolutePath,Settings.bucket)

			val gridFs = new GridFS(remoteDb, Settings.bucket)

			val fileCursor = gridFs.getFileList

			while (fileCursor.hasNext) {
				
				val fileOnRemote = fileCursor.next.asInstanceOf[GridFSDBFile]

				val c_fileName = fileOnRemote.getFilename

				log.trace("Checking remote file: %s",c_fileName)

				// val realPaths = utility.convertToRealPaths(rootDir,c_fileName)

				// val absoluteFilePath = realPaths(1)

				// val fToCreate = new File(absoluteFilePath)

				

				if (treeDb.isFileExists(c_fileName)) {
					val cMd5 = treeDb.md5For(c_fileName)

					val update = !(cMd5 == fileOnRemote.getMD5) 

					if (update)
					{
						log.debug("%s -> UPDATE for remote synch. list",c_fileName)
						// If any file on remote has different MD5 local it is marked as UPDATE on remote synch. list
						synchList.addItem(synchList.OPERATION_UPDATE,c_fileName)

					} 
				} else {

					//If any file on remote has no file on local it is marked as ADD on remote synch. list
					log.debug("%s -> ADD for remote synch. list",c_fileName)

					synchList.addItem(synchList.OPERATION_ADD,c_fileName)
				}

			
			}

			// Remote deletion check

			treeDb.resetOffset

			var next = treeDb.getNext

			while (next != null) {
				
				val remoteFilename = next(0)
				val lastMd5 = next(1)

				val fileOnRemote = gridFs.findOne( remoteFilename )

				if (fileOnRemote == null)
				{
					// If any file on local has added on local synchmfs.tree but has no value on remote, it is marked as DELETE on remote synch. list
					log.debug("%s -> DELETE for remote synch. list",remoteFilename)

					synchList.addItem(synchList.OPERATION_DELETE,remoteFilename)
				}

				next = treeDb.getNext
			}

		
		}

	}
}