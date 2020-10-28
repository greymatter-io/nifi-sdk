import java.io.File
import java.text.SimpleDateFormat
// ###########################################################################
// File Summary Report
//
// This is an auxiliary script that can be used as part of a NiFi flow to 
// append lines to a comma separated value (CSV) file that contain information
// about processed files.
//
// Documentation for usage with ExecuteGroovyScript processor available at
// https://github.com/greymatter-io/nifi-sdk/blob/master/doc/FileSummaryReport.md
// ###########################################################################

// ### Configurable variables ------------------------------------------------
// CSV Output File
def csvfile = '/home/nifi/reports/failed.csv'
// ^^^^^^^^^ THE ABOVE IS THE ONLY VARIABLES THAT SHOULD BE MODIFIED ^^^^^^^^^

// ### Get reference to a flow file ------------------------------------------
def ff = session.get()
if (!ff) return

try {

  // ### Handle to file
  f = new File(csvfile)

  // ### Write header if not yet present
  if(f.length() == 0) {
    f.append('"logdate","file","size","uuid","splitpart","originalfilename"')
  }

  // ### Get key attributes 
  def fUUID = ff.getAttribute('uuid')
  def fName = ff.getAttribute('filename')
  def fAbsPath = ff.getAttribute('absolute.path')
  def fSize = ff.getAttribute('file.size')
  def fSplitPart = ff.getAttribute('split.part')
  def fSplitOriginalFileName = ff.getAttribute('split.originalfilename')
  def fBaseOutputDirectory = ff.getAttribute('baseOutputDirectory')
  def fPath = ff.getAttribute('path')
  def fCurrentDate = new Date()

  // ### -- new line -- -------------------------------------------------------
  f.append('\n')

  // ### -- logdate -- --------------------------------------------------------
  f.append('"' + fCurrentDate + '"')
  
  // ### -- file -- -----------------------------------------------------------

  // ### Normalize absolute path
  if(fAbsPath == null) {
    // Buld from base output and path if exist
    if(fBaseOutputDirectory != null) {
      fAbsPath = fBaseOutputDirectory
    } else {
      fAbsPath = ""
    }
    if(!fAbsPath.endsWith("/")){
      fAbsPath = fAbsPath + "/"
    }
    if(fPath != null) {
      fAbsPath = fAbsPath + fPath
    }
  }
  // ### Normalize leading and trailing slashes
  // fAbsPath should end with /
  if(!fAbsPath.endsWith("/")){
    fAbsPath = fAbsPath + "/"
  }
  // fName should not lead with /
  if(fName.startsWith("/")){
    fName = fName.substring(1)
  }
  // fAbsPath should not have double slashes
  fAbsPath = fAbsPath.replaceAll("//","/")

  // ### Write out the file name
  f.append(',"' + fAbsPath + fName + '"')

  // ### -- size -- -----------------------------------------------------------

  // ### Write the file size
  f.append(',' + fSize)

  // ### -- uuid -- -----------------------------------------------------------

  // ### Write the UUID for the flow file
  f.append(',"' + fUUID + '"')

  // ### -- splitpart, originalfilename -- ------------------------------------

  // ### Write out the split part info if present
  if(fSplitPart != null) {
    if(fSplitPart.length() > 0) {
      f.append(',' + fSplitPart + ',"' + fSplitOriginalFileName + '"')
    } else { 
      f.append(',-1,"' + fAbsPath + fName + '"')
    }
  }

  // ### Done -----------------------------------------------------------------
  session.transfer(ff, REL_SUCCESS)

} catch(e) {
  try {
    // Log to console
    log.error("Error during appending file of split file", e)
    // Set attribute with error
    ff = session.putAttribute(ff, 'filesummaryreport.error.message', e.toString()) 
    // Transfer
    session.transfer(ff, REL_FAILURE)
  } catch(e2) {
    log.error("Error in FileSummaryReport")
  }
}