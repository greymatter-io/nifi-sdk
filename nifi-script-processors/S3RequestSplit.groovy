import groovy.io.FileType
import org.apache.commons.io.IOUtils
import java.nio.charset.StandardCharsets

// ###########################################################################
// S3 Request Split
//
// This is an auxiliary script that can be used as part of a NiFi flow to 
// break up requests for larger files fromn S3 into smaller segments.  
//
// Documentation for usage with ExecuteGroovyScript processor available at
// https://github.com/greymatter-io/nifi-sdk/blob/master/doc/S3RequestSplit.md
// ###########################################################################

// ### Configurable variables ------------------------------------------------
// File Part Size to split on (see man page for split)
def fps = '4G'
// Maximum File Size
def mfs = '1TB' // Whatever you need for sanity cap
// ^^^^^^^^^ THE ABOVE IS THE ONLY VARIABLES THAT SHOULD BE MODIFIED ^^^^^^^^^
def sizeStringToLong(s) {
  def o = 0
  try 
  {
    o = Long.valueOf(s)
  }
  catch(NumberFormatException nfe) 
  {
    def r = (s =~ /(\d+)/).findAll()
    if(r.size() > 0) 
    {
      def f = Long.valueOf(r[0][0]);
      switch(true) 
      {
        case (s.indexOf('KB') > -1):
          o = f * Math.pow(1000,1).longValue()
          break
        case (s.indexOf('MB') > -1):
          o = f * Math.pow(1000,2).longValue()
          break
        case (s.indexOf('GB') > -1):
          o = f * Math.pow(1000,3).longValue()
          break
        case (s.indexOf('TB') > -1):
          o = f * Math.pow(1000,4).longValue()
          break
        case (s.indexOf('PB') > -1):
          o = f * Math.pow(1000,5).longValue()
          break
        case (s.indexOf('EB') > -1):
          o = f * Math.pow(1000,6).longValue()
          break
        case (s.indexOf('B') > -1):
          o = f
          break
        case (s.indexOf('K') > -1):
          o = f << 10
          break
        case (s.indexOf('M') > -1):
          o = f << 20
          break
        case (s.indexOf('G') > -1):
          o = f << 30
          break
        case (s.indexOf('T') > -1):
          o = f << 40
          break
        case (s.indexOf('P') > -1):
          o = f << 50
          break
        case (s.indexOf('E') > -1):
          o = f << 60
          break
      }
    }
  }
  return o
}
def getPartSuffix(i)
{
  // 0  = filepart_aaa
  // 1  = filepart_aab
  // 9  = filepart_aaj
  // 28 = filepart_abc
  // 676 = filepart_baa
  def j = 26 // letters in alphabet
  def k = 97 // do as lowercase offset
  def l = Math.floor(i/(j*j))
  def m = Math.floor((i-(l*j*j))/j)
  def n = ((i-(l*j*j))-(m*j))
  def o = "filepart_" + ((char)(l+k)) + ((char)(m+k)) + ((char)(n+k))
  return o
}
// ### Get reference to a flow file ------------------------------------------
def ff = session.get()
if (!ff) return
// ### Get key attributes written by ListS3 processor ----------------------
// the name of the s3 bucket
def aS3Bucket = ff.getAttribute('s3.bucket')
// the name of the file
def aFilename = ff.getAttribute('filename')
// the etag that can be used to see if the file has changed
def aS3Etag = ff.getAttribute('s3.etag')
// a boolean indicating if this is the latest version of the object
def aS3IsLatest = ff.getAttribute('s3.isLatest')
// the last modified time in milliseconds since epoch in utc time
def aS3LastModified = ff.getAttribute('s3.LastModified')
// the size of the object in bytes
def aS3Length = ff.getAttribute('s3.length')
// the storage class of the object
def aS3StoreClass = ff.getAttribute('s3.storeClass')
// the version of the object, if applicable
def aS3Version = ff.getAttribute('s3.version')
try 
{
  // ### Normalize to long 
  def lfps = sizeStringToLong(fps)
  def lmfs = sizeStringToLong(mfs)
  // ### Verify file size does not exceed our limit --------------------------
  if(aS3Length.toBigInteger() >= lmfs.toBigInteger()) 
  {
    throw new Exception("The file size exceeds allowable limit of ${mfs}")
  }
  // ### Verify file part size > 0 -------------------------------------------
  if(lfps <= 0) 
  {
    throw new Exception("The file part size ${fps} must be greater than 0")
  }
  // ### Route to success if already small enough ----------------------------
  if(aS3Length.toBigInteger() <= lfps)
  {
    // Attribute updates
    def attrMap = [:]
    attrMap.put('s3-object-range-start', Integer.toString(0) + 'B')
    attrMap.put('s3-object-range-length', aS3Length + 'B')
    ff = session.putAllAttributes(ff, attrMap)
    // Transfer
    session.transfer(ff, REL_SUCCESS)
  }
  else
  {
    // ### Determine number of parts -------------------------------------------
    def numberOfParts = Math.floor(aS3Length.toBigInteger() / lfps)
    if ((numberOfParts * lfps) < aS3Length.toBigInteger())
    {
      numberOfParts += 1
    }
    numberOfParts = numberOfParts.toInteger()
    // ### Validate
    if (numberOfParts >= 17575) {
      throw new Exception("Number of parts to generate ${numberOfParts} exceeds allowable max ${fps}")
    }
    // ### Initialize attributes
    def attrMap = [:]
    attrMap.put('s3.bucket', aS3Bucket)
    attrMap.put('filename', aFilename)
    attrMap.put('s3.etag', aS3Etag)
    attrMap.put('s3.isLatest', aS3IsLatest)
    attrMap.put('s3.LastModified', aS3LastModified)
    attrMap.put('s3.length', aS3Length)
    attrMap.put('s3.storeClass', aS3StoreClass)
    attrMap.put('s3.version', aS3Version)
    attrMap.put('split.totalparts', (numberOfParts + 1).toString())
    attrMap.put('split.originalfilename', aFilename)
    // ### Iterate each part creating a new flow file
    for(int i = 0; i<numberOfParts; i++)
    {
      // Determine a name for the split part
      def splitPart = i + 1
      def splitPartSuffix = getPartSuffix(i)
      // Determine range start
      def rangeStart = i * lfps
      // Determine range length
      def rangeLength = (Math.floor(splitPart) != Math.floor(numberOfParts) ? lfps : aS3Length.toBigInteger() - (i * lfps))
      // Create a new flow file
      log.info("Creating new flowfile part ${splitPart} (${splitPartSuffix}) of ${lfps} bytes starting from ${rangeStart} for ${aS3Bucket}${aFilename}")
      nff = session.create(ff)
      // Attribute updates
      attrMap.put('split.part', Integer.toString(splitPart))
      attrMap.put('split.suffix', splitPartSuffix)
      attrMap.put('s3-object-range-start', rangeStart.toString() + 'B')
      attrMap.put('s3-object-range-length', rangeLength.toString() + 'B')
      nff = session.putAllAttributes(nff, attrMap)
      // Transfer
      session.transfer(nff, REL_SUCCESS)
    }
    // ### Add ZZZ file part intended to be empty
    zzz = session.create(ff)
    attrMap.put('split.part', (numberOfParts + 1).toString())   // could route on this to skip retrieval
    attrMap.put('split.suffix', getPartSuffix(17575)) // filepart_zzz
    attrMap.put('s3-object-range-start', '-1B')       // could route on this to skip retrieval
    attrMap.put('s3-object-range-length', '-1B')      // could route on this to skip retrieval
    zzz = session.putAllAttributes(zzz, attrMap)
    // Transfer the ZZZ part
    session.transfer(zzz, REL_SUCCESS)
    // ### Remove the initiating flowfile that is now split up -----------------
    session.remove(ff)
  }
} catch (e) {
  // Log to console
  log.error("Error splitting s3 requests: ${aS3Bucket}${aFilename}", e)
  // Set attribute with error
  ff = session.putAttribute(ff, 's3requestsplit.error.message', e.toString())
  // Transfer
  session.transfer(ff, REL_FAILURE)
}
