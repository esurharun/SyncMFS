package com.synchmfs {


	import java.io.File
	import com.codahale.logula.Logging
	import org.apache.log4j.Level
	
	class TreeDbValidator(treeDb: TreeDb, rootDir: File) extends Logging {

		val utility = Utility

		def updateSynchList(synchList: SynchList) {
			
			treeDb.resetOffset

			log.debug("Updating tree db synch. list for %s",rootDir.getAbsolutePath)

			var next = treeDb.getNext

			while (next != null) {
				
				val remoteFilename = next(0)
				val lastMd5 = next(1)

				val filePath = utility.convertToRealPaths(rootDir, remoteFilename)(1)

				val file = new File(filePath)

				log.trace("%s checking for local",remoteFilename)
				if (file.exists) {
					
					//If the file described on .syncmfs.tree is exists but not has same MD5 on current tree, it is marked as UPDATE on synch list

					val actualMd5 = utility.getMD5(file)

					if (actualMd5 != lastMd5)
					{

						log.debug("%s -> UPDATE for local synch list",remoteFilename)
						synchList.addItem(synchList.OPERATION_UPDATE,remoteFilename)
					}

						


				} else {
					//If the file described on syncmfs.tree is not exists on current directory tree, it is marked as DELETE on synch list
					log.debug("%s -> DELETE for local synch list",remoteFilename)
					synchList.addItem(synchList.OPERATION_DELETE,remoteFilename)
				}

				next = treeDb.getNext

			}
		}
		
	}



}