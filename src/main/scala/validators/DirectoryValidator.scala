package com.synchmfs {
	

	import java.io.File

	import com.codahale.logula.Logging
	import org.apache.log4j.Level
	
	class DirectoryValidator(treeDb: TreeDb, rootDir: File) extends Logging {

		val utility = Utility

		def updateSynchList(synchList: SynchList) {
			
			treeDb.resetOffset
			
			log.debug("Updating directory synch. list for %s",utility.shortenFilenameFromBeginning(rootDir.getAbsolutePath))


			val parentPath = utility.getParentPaths(rootDir)

			def walkThroughDir(dir: File) {
			
			
				val files: Array[File] = dir.listFiles()
				
				files.foreach( file => {
				
					if (file.isDirectory()) {
				
						walkThroughDir(file)
				
					} else {
						
						//putFile(file,database)

						val remoteFilePath = utility.getPathRelativetoParentPaths(file, parentPath )

						log.trace("%s checking for tree db",utility.shortenFilenameFromBeginning(remoteFilePath))

						if (!treeDb.isFileExists(remoteFilePath))
						{
							
							if (remoteFilePath != ".syncmfs.db/syncmfs.tree") {

								log.debug("%s -> ADD for local synch. list",utility.shortenFilenameFromBeginning(remoteFilePath))

								synchList.addItem(synchList.OPERATION_ADD,remoteFilePath)
							}
						}

					}

				})
				
			}

			walkThroughDir(rootDir)

		


			
		}
		
	}

}