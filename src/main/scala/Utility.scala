package com.synchmfs {

	import java.io.File
	import java.io.FileInputStream
	import java.security.DigestInputStream
	import java.security.MessageDigest

	object Utility {
		

		/*
			Returns parent path tree 
		*/
		def getParentPaths(file: File): Array[String] = {
			
			var parentPathLst: List[String] = List[String]()

			var currDir = file
				
			while (currDir != null && currDir.getName().trim() != "") {
				
				parentPathLst ++= List(currDir.getName())

				currDir = currDir.getParentFile()
			}

			parentPathLst.toArray

		}


		def replaceSeparatorToStar(in: String): String = in.replaceAll("\\/","*")
		def replaceStarToSeparator(in: String): String = in.replaceAll("\\*","/")

		def getPathRelativetoParentPaths(file: File,parentPaths: Array[String]):String = {
			
			 val fileParentPaths: Array[String] = getParentPaths(file).reverse

			 var filePathStr:String = ""

			 fileParentPaths.foreach( fp => {
			 	filePathStr += replaceSeparatorToStar(fp)+"/"

			 })
			
			 var rootPathStr:String = ""

			 parentPaths.reverse.foreach( fp => {
			 	rootPathStr += replaceSeparatorToStar(fp)+"/"

			 })

			//println(filePathStr)

			//println("--> "+filePathStr+" - "+rootPathStr)

			 if (filePathStr.startsWith(rootPathStr)) {
			 	val rpath : String = filePathStr.substring(rootPathStr.length)
			 	// If there is file the algorith adds unnecessary / at the 
			 	// end of the path. So we are removing it if it is file
			 	if (file.isDirectory)
			  	 	rpath
			  	else {
			  		rpath.substring(0,rpath.length-1)
			  	}
			 }
			 else
			  	null
			 

			

			
		}

		def convertToRealPaths(rootDir: File, remote_fileName: String): Array[String] = {
			

			val paths = remote_fileName.split("/")

			var absoluteDirPath = rootDir.getAbsolutePath()

			for (i <- 0 to paths.length-2) {
				
				absoluteDirPath = absoluteDirPath + File.separator + replaceStarToSeparator(paths(i))	

			}

			val absoluteFilePath = absoluteDirPath + File.separator + replaceStarToSeparator(paths(paths.length-1))


			Array[String](absoluteDirPath, absoluteFilePath)

		}		



		def getMD5(file: File): String = {
				

 				val md5 = MessageDigest.getInstance("MD5");
                md5.reset();
                val is = new DigestInputStream( new FileInputStream(file) , md5 );
                var read = 0;
                while ( is.read() >= 0 ) { 
                    read = read + 1;
                    val r = is.read(  new Array[Byte](1024) ); 
                    if ( r > 0 )
                    	read =  read + r;
                }
                val digest = md5.digest();
	            //    System.out.println( "length: " + read + " md5: " + Util.toHex( digest ) );

	            return com.mongodb.util.Util.toHex( digest )

		}

	}
	
	
	
}