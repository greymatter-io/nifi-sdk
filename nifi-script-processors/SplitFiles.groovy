import groovy.io.FileType
import org.apache.commons.io.IOUtils
import java.nio.charset.StandardCharsets

// ###########################################################################
// Split Files
//
// This is an auxiliary script that can be used as part of a NiFi flow to 
// break up larger files into smaller segments.  
//
// Documentation for usage with ExecuteGroovyScript processor available at
// https://github.com/greymatter-io/nifi-sdk/blob/master/doc/SplitFiles.md
// ###########################################################################

// ### Configurable variables ------------------------------------------------
// Temp folder location 
def tf = '/home/nifi/tempfiles'
// Maximum File Size
def mfs = '1000000000000' // 1 TB.  E.g. Our work disk is about 3.6 TB
// File Part Size to split on (see man page for split)
def fps = '4G'
// ^^^^^^^^^ THE ABOVE IS THE ONLY VARIABLES THAT SHOULD BE MODIFIED ^^^^^^^^^
// ### Get reference to a flow file ------------------------------------------
def ff = session.get()
if (!ff) return
// ### Get key attributes written by ListFile processor ----------------------
def fName = ff.getAttribute('filename')
def fPath = ff.getAttribute('path')
def fAbsPath = ff.getAttribute('absolute.path')
def fOwner = ff.getAttribute('file.owner')
def fGroup = ff.getAttribute('file.group')
def fPermissions = ff.getAttribute('file.permissions')
def fSize = ff.getAttribute('file.size')
def fLastModifiedTime = ff.getAttribute('file.lastModifiedTime')
def fLastAccessTime = ff.getAttribute('file.lastAccessTime')
def fCreationTime = ff.getAttribute('file.creationTime')
try {
  // ### Verify running under linux
  if(!(System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH).indexOf("nux") >= 0)) {
    throw new Exception("This script processor has dependencies that require running under Linux");
  }

  // ### Verify file size does not exceed our limit --------------------------
  if(fSize.toBigInteger() >= mfs.toBigInteger()) {
    throw new Exception("The file size exceeds allowable limit of ${mfs}")
  }
  // ### Determine revised path and output path for split --------------------
  def rPath = fPath + fName
  def oPath = tf + '/' + rPath
  // ### Create output folder for split --------------------------------------
  def oFolder = new File(oPath)
  if (!oFolder.exists()) {
    oFolder.mkdirs()
  } else {
    throw new Exception("Split folder already exists at ${oPath}")
  }
  // ### Perform the split ---------------------------------------------------
  log.info("Splitting file ${fAbsPath}${fName}")
  def sCmd = 'split --suffix-length=3 --bytes=' + fps + ' ' + fAbsPath + fName
  sCmd = sCmd + ' filepart_'
  def sEnv = []
  Process sProc = sCmd.execute(sEnv, oFolder)
  def sOut = new StringBuffer()
  def sErr = new StringBuffer()
  sProc.consumeProcessOutput(sOut, sErr)
  sProc.waitFor()
  if (sErr.size() > 0) {
    throw new Exception("Split had an error: ${sErr}")
  }
  // ### Create a zero byte file as terminator -------------------------------
  def zeroFilePath = oPath + '/filepart_zzz'
  def zeroFile = new File(zeroFilePath)
  zeroFile.createNewFile()
  // ### Get List of the created files ---------------------------------------
  def fileList = []
  oFolder.eachFileRecurse(FileType.FILES) { file ->
    fileList << file
  }
  fileList.sort();
  def splitSize = fileList.size()
  def splitPart = 0
  // ### Create flowfiles for each generated file ----------------------------
  fileList.each {
    // Set an attribute indicating this is a "part" from a split
    splitPart ++
    // Create a new flow file
    log.info("Creating new flowfile part ${splitPart} of ${splitSize} for ${fAbsPath}${fName}")
    nff = session.create(ff)
    // Attribute updates
    def attrMap = [:]
    attrMap.put('split.part', Integer.toString(splitPart)) 
    attrMap.put('split.totalparts', Integer.toString(splitSize))
    attrMap.put('split.originalfilename', fAbsPath + fName)
    attrMap.put('filename', it.getName())
    attrMap.put('path', rPath)
    attrMap.put('absolute.path', oPath)
    attrMap.put('file.owner', fOwner)
    attrMap.put('file.group', fGroup)
    attrMap.put('file.permissions', fPermissions)
    attrMap.put('file.size', Long.toString(it.length()))
    attrMap.put('file.LastModifiedTime', fLastModifiedTime)
    attrMap.put('file.LastAccessTime', fLastAccessTime)
    attrMap.put('file.creationTime', fCreationTime)
    nff = session.putAllAttributes(nff, attrMap)
    // Transfer
    session.transfer(nff, REL_SUCCESS)
  }
  // ### Remove the initiating flowfile that is now split up -----------------
  session.remove(ff)
} catch (e) {
  try {
    // Log to console
    log.error("Error during split of large file: ${fName}", e)
    // Set attribute with error
    ff = session.putAttribute(ff, 'splitfiles.error.message', e.toString())
    // Transfer
    session.transfer(ff, REL_FAILURE)
  } catch (e2) {
    log.error("Error in SplitFiles")
    log.error(e2);
  }
}