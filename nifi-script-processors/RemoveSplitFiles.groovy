import groovy.io.FileType
import org.apache.commons.io.IOUtils
import java.nio.charset.StandardCharsets

// ###########################################################################
// Remove Split Files
//
// This is an auxiliary script that can be used as part of a NiFi flow to 
// remove a split file once it has been loaded into a flow file.  This allows
// for cleaning up temporary files as they are being worked, to free up disk 
// space.  In addition to removing the split file, the folder path leading to 
// the split file part will also be removed if there are no other files 
// contained within it.
//
// Documentation for usage with ExecuteGroovyScript processor available at
// https://github.com/greymatter-io/nifi-sdk/blob/master/doc/RemoveSplitFiles.md
// ###########################################################################

// ### Configurable variables ------------------------------------------------
// Temp folder location 
def tf = '/home/nifi/tempfiles'
// ^^^^^^^^^ THE ABOVE IS THE ONLY VARIABLES THAT SHOULD BE MODIFIED ^^^^^^^^^

// ### Get reference to a flow file ------------------------------------------
def ff = session.get()
if (!ff) return

try {

  // ### Get key attributes --------------------------------------------------
  def fName = ff.getAttribute('filename')
  def fPath = ff.getAttribute('path')
  def fAbsPath = ff.getAttribute('absolute.path')
  def fSplitPart = ff.getAttribute('split.part')

  // ### Check if this is a split file that needs deleted --------------------
  if(fAbsPath.indexOf(tf) > -1) {
    if(fSplitPart.length() > 0) {
      File file = new File(fAbsPath + '/' + fName)
      File parent = new File(fAbsPath)
      def parentContainsTF = parent.getAbsolutePath().indexOf(tf) > -1
      def fileCount = parent.listFiles().length
      def wasDeleted = false
      if(file.isFile()) {
        fileCount = fileCount - 1
        wasDeleted = file.delete()
      }
      while(wasDeleted && fileCount == 0 && parentContainsTF) {
        File nextParent = parent.getParentFile()
        wasDeleted = parent.deleteDir()
        parent = nextParent
        parentContainsTF = parent.getAbsolutePath().indexOf(tf) > -1    
        fileCount = parent.listFiles().length
      }
    }
  }

  // ### Done ----------------------------------------------------------------
  session.transfer(ff, REL_SUCCESS)

} catch(e) {
  try {
    // Log to console
    log.error("Error during deletion of split file", e)
    // Set attribute with error
    ff = session.putAttribute(ff, 'removefile.error.message', e.toString())
    // Transfer
    session.transfer(ff, REL_FAILURE)
  } catch (e2) {
    log.error("Error in RemoveSplitFiles")
    log.error(e2);
  }
}
