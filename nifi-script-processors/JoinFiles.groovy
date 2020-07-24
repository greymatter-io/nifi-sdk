import groovy.io.FileType
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import java.nio.charset.*

// ###########################################################################
// Join Files
//
// This is an auxiliary script that can be used as part of a NiFi flow to 
// rejoin files in a folder that were previously split up.  
//
// Documentation for usage with ExecuteGroovyScript processor available at
// https://github.com/greymatter-io/nifi-sdk/blob/master/doc/JoinFiles.md
// ###########################################################################

// ### Configurable variables ------------------------------------------------
// File Part Size in bytes previously split on (see man page for split)
def fps = '4G' // 4GB = 4*1024*1024*1024 = 4294967296
// ^^^^^^^^^ THE ABOVE IS THE ONLY VARIABLES THAT SHOULD BE MODIFIED ^^^^^^^^^
def lfhs = -1
try 
{
  lfhs = Long.valueOf(fps)
}
catch(NumberFormatException nfe) 
{
  def r = (fps =~ /(\d+)/).findAll()
  if(r.size() > 0) 
  {
    f = Long.valueOf(r[0][0]);
    switch(true) {
      case (fps.indexOf('KB') > -1):
        lfhs = f * Math.pow(1000,1).longValue()
        break
      case (fps.indexOf('MB') > -1):
        lfhs = f * Math.pow(1000,2).longValue()
        break
      case (fps.indexOf('GB') > -1):
        lfhs = f * Math.pow(1000,3).longValue()
        break
      case (fps.indexOf('TB') > -1):
        lfhs = f * Math.pow(1000,4).longValue()
        break
      case (fps.indexOf('PB') > -1):
        lfhs = f * Math.pow(1000,5).longValue()
        break
      case (fps.indexOf('EB') > -1):
        lfhs = f * Math.pow(1000,6).longValue()
        break
      case (fps.indexOf('K') > -1):
        lfhs = f << 10
        break
      case (fps.indexOf('M') > -1):
        lfhs = f << 20
        break
      case (fps.indexOf('G') > -1):
        lfhs = f << 30
        break
      case (fps.indexOf('T') > -1):
        lfhs = f << 40
        break
      case (fps.indexOf('P') > -1):
        lfhs = f << 50
        break
      case (fps.indexOf('E') > -1):
        lfhs = f << 60
        break
    }
  }
}
// ### Get reference to a flow file ------------------------------------------
def ff = session.get()
if (!ff) return
// ### Get key attributes needed from inbound flow ---------------------------
def fDir = ff.getAttribute('baseOutputDirectory') //     /home/nifi/outfiles
def fName = ff.getAttribute('filename')           //     filepart_aac
def fSize = ff.getAttribute('file.size')          //     414515200
def fPath = ff.getAttribute('path')               //     /SSLInfoData01.ndf/

def inFile
def inBuffered
def outFile
def outBuffered

try 
{
  // ### Check if this is a file part ----------------------------------------
  def isFilePart = (fName.indexOf('filepart_') == 0)
  // ### Check if file size matches file part size ---------------------------
  def isFileSplitSize = (fSize.toBigInteger() == lfhs.toBigInteger())
  // ### Check if ready to process -------------------------------------------
  switch(true) 
  {
    // Files that are not part of a file part do not need processing
    case (!isFilePart):
      break
    // Files that are the split size likely do not need processing
    case (isFileSplitSize):
      // Empty flowfile content as its already been written to disk and
      // this script processor will set the flowfile content of the flowfile
      // that corresponds to the last part of the file when it processes
      ff = session.write(ff, {out ->
        sessionOutputStream = new BufferedOutputStream(out)
        sessionOutputStream.flush()
      } as OutputStreamCallback)
      break
    // Files that are part of a file and are the last file in the set
    case (isFilePart && !isFileSplitSize):

      // Get combined file name from absolute path and validate
      if(fPath.size() == 0) 
      {
        throw new Exception("path is empty indicating no input files or place to put output")
      }
      if(fPath == "/") 
      {
        throw new Exception("path cannot be the root")
      }

      // Get path without trailing slash
      def fPath2 = fPath[0..-2]

      // Handle to folder
      def oFolder = new File(fDir + fPath2)
      // Add all files to a map
      def fileList = []
      oFolder.eachFileRecurse(FileType.FILES) 
      { 
        file ->
        fileList << file
      }
      fileList.sort();
      // Start join file
      def joinedFilePath = fDir + fPath2 + '.joined'
      joinFile = new File(joinedFilePath)
      if(joinFile.delete())
      {
          log.info("Deleted " + joinedFilePath)
      }
      outFile = new FileOutputStream(joinedFilePath)
      outBuffered = new BufferedOutputStream(outFile)

      // Process each file
      fileList.each 
      {
        inFile = new FileInputStream(fDir + fPath + it.getName())
        inBuffered = new BufferedInputStream(inFile)
		int b;
		while ((b = inBuffered.read()) != -1) 
        {
			outBuffered.write(b)
		}
        outBuffered.flush()
		if(inFile!=null) inFile.close()
        if(inBuffered!=null) inBuffered.close()
      }

      // Close join file
      if(outFile!=null) outFile.close()
      if(outBuffered!=null) outBuffered.close()

      // Remove the folder of file parts
      FileUtils.cleanDirectory(oFolder)
      oFolder.delete()

      // Rename the joined file
      boolean success = joinFile.renameTo(oFolder)
      if(!success)
      {
        throw new Exception("Renaming joined file was not successful")
      }

      // Attribute updates
      def modifiedPath = fPath.replaceAll(oFolder.getName() + "/", "")
      def joinFile2 = new File(fDir + fPath2)
      def attrMap = [:]
      attrMap.put('filename', oFolder.getName())
      attrMap.put('path', modifiedPath)
      attrMap.put('absolute.path', oFolder.getParent())
      attrMap.put('file.size', Long.toString(joinFile2.length()))
      ff = session.putAllAttributes(ff, attrMap)

      // Update Flowfile Content
      // The contents of our joined file is what we want in the flow file
      joinFileInputStream = new FileInputStream(fDir + fPath2)
      joinBufferedIS = new BufferedInputStream(joinFileInputStream)
      ff = session.write(ff, {out ->
        int b;
        sessionOutputStream = new BufferedOutputStream(out)
        while ((b = joinBufferedIS.read()) != -1) 
        {
          sessionOutputStream.write(b)
        }
        sessionOutputStream.flush()
        if(joinFileInputStream!=null) joinFileInputStream.close()
        if(joinBufferedIS!=null) joinBufferedIS.close()
      } as OutputStreamCallback)
      if(joinFileInputStream!=null) joinFileInputStream.close()
      if(joinBufferedIS!=null) joinBufferedIS.close()
      if(sessionOutputStream!=null) sessionOutputStream.close()
      

      break
  }
  // Transfer
  session.transfer(ff, REL_SUCCESS)
}
catch (e) 
{
  // Log to console
  log.error("Error during join of file parts: ${fDir}${fPath}${fName}", e)
  // Set attribute with error
  ff = session.putAttribute(ff, 'joinfiles.error.message', e.toString())
  // Transfer
  session.transfer(ff, REL_FAILURE)
}
finally
{
  if(inFile!=null) inFile.close();
  if(inBuffered!=null) inBuffered.close();
  if(outFile!=null) outFile.close();
  if(outBuffered!=null) outBuffered.close();		
}
