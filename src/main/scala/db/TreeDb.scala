
package com.synchmfs {
	import java.sql._
	import java.io.File

        import com.codahale.logula.Logging
        import org.apache.log4j.Level

	class TreeDb(rootDir: String) extends Logging {

		/*
			SQL CONSTANTS
		*/
		val SQL_CREATE_TREE_TABLE = "CREATE TABLE TREE (FILENAME TEXT PRIMARY KEY, MD5 TEXT) "
		val SQL_FILENAME_COUNT = "SELECT COUNT(*) AS C FROM TREE WHERE FILENAME = ?"
		val SQL_GET_MD5 = "SELECT MD5 FROM TREE WHERE FILENAME = ?"
		val SQL_INSERT_FILE_ENTITY = "INSERT INTO TREE (FILENAME,MD5) VALUES(?,?)"
		val SQL_DELETE_FILE_ENTITY = "DELETE FROM TREE WHERE FILENAME = ?"
		val SQL_GET_NEXT = "SELECT * FROM TREE LIMIT 1 OFFSET %d"


		/* 
			LOCAL CONSTANTS 
		*/
		
		val C_TREE_DB_DIR = rootDir+File.separator+Settings.TREE_DB_DIR
		val DB_PATH = C_TREE_DB_DIR+File.separator+Settings.DB_FILENAME
		val FILE_C_TREE_DB_DIR = new File(C_TREE_DB_DIR)

		var offset = 0

		/*
			CONSTRUCTION
		*/
		var create_db = false

		if (!FILE_C_TREE_DB_DIR.exists)
		{
			create_db = true
			if (!FILE_C_TREE_DB_DIR.mkdir) {
				throw new Exception("Cannot create "+C_TREE_DB_DIR)
			}
		}

		if (!new File(DB_PATH).exists)
			create_db = true

		Class.forName("org.sqlite.JDBC");
        
        val conn = DriverManager.getConnection("jdbc:sqlite:"+DB_PATH);
		val statement = conn.createStatement


        if (create_db)
        {

                val sql = SQL_CREATE_TREE_TABLE

                log.trace("SQL: %s",sql)

        	statement.executeUpdate(sql)
        }

        def resetOffset {
      		offset = 0
      	}

       

        def getNext: scala.Array[String] = {

                val sql = SQL_GET_NEXT format offset

                //log.trace("SQL: %s",sql)


        	val rs = statement.executeQuery(sql)

        	if (!rs.next)
        		return null
        	
        	offset = offset + 1
        	var ret = new scala.Array[String](2);
        	ret(0) = rs.getString("FILENAME")
			ret(1) = rs.getString("MD5")


        	rs.close()

        	return ret;
        }

        def isFileExists(fileName: String): Boolean = {
        	
                val pStat = conn.prepareStatement(SQL_FILENAME_COUNT)
                pStat.setString(1,fileName)

                // val sql = SQL_FILENAME_COUNT format fileName

                // log.trace("SQL: %s",sql)

        	val rs = pStat.executeQuery

        	var ret = false;
        	while (rs.next) {
        		ret = (rs.getInt("C") > 0)
        	}

        	rs.close()
                pStat.close()

        	return ret;
        }

        def addFile(fileName:String, md5: String) {

                 val pStat = conn.prepareStatement(SQL_INSERT_FILE_ENTITY)


                pStat.setString(1,fileName)
                pStat.setString(2,md5)
                pStat.executeUpdate

        }

        def removeFile(fileName:String) {

         //        val sql = SQL_DELETE_FILE_ENTITY format fileName

         //        log.trace("SQL: %s",sql)

        	// statement.executeUpdate(sql)

                val pStat = conn.prepareStatement(SQL_DELETE_FILE_ENTITY)


                pStat.setString(1,fileName)
                pStat.executeUpdate
        }

        def md5For(fileName:String): String = {


                // val sql = SQL_GET_MD5 format fileName

                // log.trace("SQL: %s",sql)

                val pStat = conn.prepareStatement(SQL_GET_MD5)


                pStat.setString(1,fileName)
                

		val rs = pStat.executeQuery

        	var ret: String = null;
        	while (rs.next) {
        		ret = rs.getString("MD5")
        	}

        	rs.close()
                pStat.close()

        	return ret;
        }

        def close {
        	statement.close
        	conn.close
        }


		
	}
}