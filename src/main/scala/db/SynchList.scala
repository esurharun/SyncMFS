
package com.synchmfs {
	import java.sql._
	import java.io.File

	class SynchList {

	val OPERATION_DELETE: Int = 0
	val OPERATION_ADD: Int = 1
	val OPERATION_UPDATE: Int = 2

	val tempFile : File = File.createTempFile("TEMP_LIST_",".syncmfs")

	/*
		SQL CONSTANTS
	*/
	val SQL_CREATE_LIST_TABLE = "CREATE TABLE LIST (OPERATION INTEGER, FILENAME TEXT) "
	val SQL_INSERT_ITEM = "INSERT INTO LIST (OPERATION,FILENAME) VALUES('%d','%s')"		
	val SQL_GET_NEXT = "SELECT * FROM LIST LIMIT 1 OFFSET %d"
        val SQL_SELECT_BY_FILENAME = "SELECT * FROM LIST WHERE FILENAME = '%s'"

	

	Class.forName("org.sqlite.JDBC");
        
        val conn = DriverManager.getConnection("jdbc:sqlite:"+tempFile.getAbsolutePath());
		val statement = conn.createStatement


        statement.executeUpdate(SQL_CREATE_LIST_TABLE)

      
      	var offset = 0

      	def resetOffset {
      		offset = 0
      	}

        def getOperationForFile(fileName:String): Int = {
                val rs = statement.executeQuery(SQL_SELECT_BY_FILENAME format fileName)

                if (!rs.next)
                        return -1
                
                val ret = rs.getInt("OPERATION")


                rs.close()

                return ret;
        }

        def addItem(operation:Int,fileName:String) {
        	statement.executeUpdate(SQL_INSERT_ITEM format (operation, fileName))
        }

        def getNext: scala.Array[String] = {
        	val rs = statement.executeQuery(SQL_GET_NEXT format offset)

        	if (!rs.next)
        		return null
        	
        	offset = offset + 1
        	var ret = new scala.Array[String](2);
        	ret(0) = String.valueOf(rs.getInt("OPERATION"))
			ret(1) = rs.getString("FILENAME")


        	rs.close()

        	return ret;
        }

        def printOut {
                
                resetOffset
                var next = getNext
                while (next != null) {
                        printf("OP: %s - FILE: %s\n", if (next(0) == "0") "DELETE" else if (next(0) == "1") "ADD" else "UPDATE", next(1))

                        next = getNext
                }

        }


        def close {
        	statement.close
        	conn.close

        	tempFile.delete
        }


		
	}
}