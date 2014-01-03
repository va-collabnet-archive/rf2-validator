package gov.va.rf2.validator;

import gov.va.oia.terminology.converters.sharedUtils.ConsoleUtil;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.UUID;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import au.com.bytecode.opencsv.CSVReader;

/**
 * Goal which validates a set of RF2 output files
 * 
 * @goal rf2-validate
 * 
 * @phase process-sources
 */
public class RF2ValidatorMojo extends AbstractMojo
{
	/**
	 * Where to put the output file.
	 * 
	 * @parameter expression="${project.build.directory}"
	 * @required
	 */
	private File outputDirectory;

	/**
	 * Location of the RF2 input data files. Expected to be a directory.
	 * 
	 * @parameter
	 * @required
	 */
	private File inputFile;
	
	private BufferedWriter outputFile;
	
	//YYYYMMDD
	SimpleDateFormat sdf1 = new SimpleDateFormat("yyyyMMdd");
	
	//YYYYMMDDThhmmssZ
	//Note - the pattern requires java 1.7
	SimpleDateFormat sdf2 = new SimpleDateFormat("yyyyMMdd'T'HHmmssX");
	
	private int errorCounter = 0;
	private int fileCounter = 0;
	private int validFileCounter = 0;
	

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException
	{
		try
		{
			if (!outputDirectory.exists())
			{
				outputDirectory.mkdirs();
			}

			outputFile = new BufferedWriter(new FileWriter(new File(outputDirectory, "report.txt")));
			ConsoleUtil.println("Validating RF2 Export");

			processFolder(inputFile);

			writeLine("Processed " + fileCounter + " files, " + validFileCounter + " were valid, " + (fileCounter - validFileCounter) + " had errors");
			
			outputFile.close();
			
			
			
			//TODO use BdbTestRunner to validate concepts I find against the DB 

		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new MojoExecutionException("oops", e);
		}
	}

	private void processFolder(File folder) throws IOException
	{
		for (File f : folder.listFiles())
		{
			if (f.isDirectory())
			{
				processFolder(f);
			}
			else
			{
				int startErrorCount = errorCounter;
				startFile(f);
				FileInfo fi = new FileInfo(f);
				if (f.getName().toLowerCase().matches("[a-zA-Z0-9\\-_]+\\.[a-zA-Z0-9]{1,4}"))
				{
					if (f.getName().length() > 128)
					{
						error("Max file name length should be 128 characters - this file is " + f.getName().length());
					}
					String[] nameParts = f.getName().substring(0, f.getName().length() - 4).split("_");
					if (nameParts.length != 5 )
					{
						error("Invalid number of elements in the file name.  Expected 5, had " + nameParts.length);
					}
					
					int partNo = 1;
					boolean[]foundParts = new boolean[5];
					for (String part : nameParts)
					{
						int returnedPart = checkPart(part, partNo, fi);
						if (returnedPart > 0)
						{
							foundParts[returnedPart - 1] = true;
							partNo = returnedPart + 1;
						}
						else
						{
							partNo++;
						}
					}
					for (int i = 0; i < 5; i++)
					{
						if (!foundParts[i])
						{
							error("Didn't find part number " + (i + 1));
						}
					}
					
					fi.setExtension(f.getName().substring(f.getName().lastIndexOf('.')).toLowerCase());
				}
				else
				{
					error("Illegal file extension");
				}
				
				if (fi.getExtension().equals(".txt"))
				{
					checkLineReturn(f);
					CSVReader r = new CSVReader(new InputStreamReader(new BOMInputStream(new FileInputStream(f)), "UTF-8"), '\t');
					String[] header = r.readNext();
					if (header == null || header.length == 0)
					{
						error("File is missing the required header line");
					}
					
					HashMap<Integer, DataType> columnInfo = parseHeader(header, fi);
					
					if (columnInfo.size() > 0 && columnInfo.size() == header.length)
					{
						String[] row = r.readNext();
						int lineNo = 2;
						while (row != null)
						{
							if (row.length != header.length)
							{
								error("Line " + lineNo + " should have " + header.length + " columns, but it has " + row.length);
							}
							else
							{
								validateRow(row, columnInfo, lineNo);
							}
							row = r.readNext();
							lineNo++;
						}
					}
					r.close();
				}
				writeLine("");
				fileCounter++;
				if (errorCounter == startErrorCount)
				{
					validFileCounter++;
				}
			}
		}
	}
	
	private void validateRow(String[] row, HashMap<Integer, DataType> columnInfo, int lineNo) throws IOException
	{
		for (int i = 0; i < row.length; i++)
		{
			String result = parseData(row[i], columnInfo.get(i));
			if (result != null)
			{
				error("Data on line " + lineNo + " column " + (i + 1) + " is illegal - " + result);
			}
		}
	}
	
	private String parseData(String data, DataType dataType)
	{
		if (dataType == null)
		{
			return "malformed data file, unknown what the column should be";
		}
		else
		{
			switch (dataType)
			{
				case Integer:
					try
					{
						Integer.parseInt(data);
					}
					catch (NumberFormatException e)
					{
						return "should be an Integer";
					}
					break;
				case Boolean:
					if (!(data.equals("0") || data.equals("1")))
					{
						return "should be '0' (false) or '1' (true)";
					}
					break;
				case SCTID:
					try
					{
						checkSCTID(data);
					}
					catch (Exception e)
					{
						return "should be a SCTID"; 
					}
					break;
				case SCTIDorUUID:
					try
					{
						if (data.length() == 36)
						{
							UUID.fromString(data);
						}
						else
						{
							checkSCTID(data);
						}
					}
					catch (Exception e)
					{
						return "should be a SCTID or UUID"; 
					}
					break;
				case String:
					if (data.length() == 0)
					{
						return "No data found";
					}
					break;
				case Time:
					try
					{
						if (data.length() == 8)
						{
							sdf1.parse(data);
						}
						else
						{
							sdf2.parse(data);
						}
					}
					catch (Exception e)
					{
						return "unparsable time value";
					}
					break;
				case UUID:
					try
					{
						UUID.fromString(data);
					}
					catch (Exception e)
					{
						return "should be a UUID"; 
					}
					break;
			}
			return null;
		}
	}
	
	public void checkSCTID(String sctId) throws Exception
	{
		if (sctId.length() < 6 || sctId.length() > 18)
		{
			throw new Exception("Invalid SCTID length " + sctId);
		}
		
		VerhoeffDihedralCheck.validateCheckDigit(sctId);
		String partition = sctId.substring(sctId.length() - 3, sctId.length() - 1);
		
		if (!partition.matches("(0[0-5])|(1[0-5])"))
		{
			throw new Exception("Invalid partition portion of SCTID '" + partition + "'");
		}
	}
	
	private HashMap<Integer, DataType> parseHeader(String[] header, FileInfo fi) throws IOException
	{
		HashMap<Integer, DataType> result = new HashMap<Integer, DataType>();
		
		if (fi.getContentType() == null)
		{
			error("Unknown content type (due to invalid file naming), can't validate file");
		}
		else if (fi.getContentType().equals("Concept"))
		{
			setupColumn(result, DataType.SCTID, 0, header, "id");
			setupColumn(result, DataType.Time, 1, header, "effectiveTime");
			setupColumn(result, DataType.Boolean, 2, header, "active");
			setupColumn(result, DataType.SCTID, 3, header, "moduleId");
			setupColumn(result, DataType.SCTID, 4, header, "definitionStatusId");
			if (header.length > 5)
			{
				error("Too many columns - should have 5, but has " + header.length);
			}
		}
		else if (fi.getContentType().equals("Description"))
		{
			setupColumn(result, DataType.SCTID, 0, header, "id");
			setupColumn(result, DataType.Time, 1, header, "effectiveTime");
			setupColumn(result, DataType.Boolean, 2, header, "active");
			setupColumn(result, DataType.SCTID, 3, header, "moduleId");
			setupColumn(result, DataType.SCTID, 4, header, "conceptId");
			setupColumn(result, DataType.String, 5, header, "languageCode");
			setupColumn(result, DataType.SCTID, 6, header, "typeId");
			setupColumn(result, DataType.String, 7, header, "term");
			setupColumn(result, DataType.SCTID, 8, header, "caseSignificanceId");
			if (header.length > 9)
			{
				error("Too many columns - should have 9, but has " + header.length);
			}
		}
		else if (fi.getContentType().equals("Relationship"))
		{
			setupColumn(result, DataType.SCTID, 0, header, "id");
			setupColumn(result, DataType.Time, 1, header, "effectiveTime");
			setupColumn(result, DataType.Boolean, 2, header, "active");
			setupColumn(result, DataType.SCTID, 3, header, "moduleId");
			setupColumn(result, DataType.SCTID, 4, header, "sourceId");
			setupColumn(result, DataType.SCTID, 5, header, "destinationId");
			setupColumn(result, DataType.Integer, 6, header, "relationshipGroup");
			setupColumn(result, DataType.SCTID, 7, header, "typeId");
			setupColumn(result, DataType.SCTID, 8, header, "characteristicTypeId");
			setupColumn(result, DataType.SCTID, 9, header, "modifierId");
			if (header.length > 10)
			{
				error("Too many columns - should have 10, but has " + header.length);
			}
		}
		else if (fi.getContentType().equals("Identifier"))
		{
			setupColumn(result, DataType.SCTID, 0, header, "identifierSchemeId");
			setupColumn(result, DataType.String, 1, header, "alternateIdentifier");
			setupColumn(result, DataType.Time, 2, header, "effectiveTime");
			setupColumn(result, DataType.Boolean, 3, header, "active");
			setupColumn(result, DataType.SCTID, 4, header, "moduleId");
			setupColumn(result, DataType.SCTID, 5, header, "referencedComponentId");
			if (header.length > 6)
			{
				error("Too many columns - should have 6, but has " + header.length);
			}
		}
		else if (fi.getContentType().endsWith("Refset"))
		{
			String prefix = fi.getContentType().substring(0, fi.getContentType().indexOf("Refset"));
			setupColumn(result, DataType.UUID, 0, header, "id");
			setupColumn(result, DataType.Time, 1, header, "effectiveTime");
			setupColumn(result, DataType.Boolean, 2, header, "active");
			setupColumn(result, DataType.SCTID, 3, header, "moduleId");
			setupColumn(result, DataType.SCTID, 4, header, "refsetId");
			setupColumn(result, DataType.SCTIDorUUID, 5, header, "referencedComponentId");
			
			for (int i = 0; i < prefix.length(); i++)
			{
				char c = prefix.charAt(i);
				if (c == 'c')
				{
					setupColumn(result, DataType.SCTID, 6 + i, header, "");
				}
				else if (c == 's')
				{
					setupColumn(result, DataType.String, 6 + i, header, "");
				}
				else if (c == 'i')
				{
					setupColumn(result, DataType.Integer, 6 + i, header, "");
				}
				else
				{
					error("Invalid extra column type '" + c + "'");
					setupColumn(result, DataType.String, 6 + i, header, "");
				}
			}
			if (header.length > (6 + prefix.length()))
			{
				error("Too many columns - should have " + (6 + prefix.length()) + ", but has " + header.length);
			}
		}
		else
		{
			error("Content Type '" + fi.getContentType() + "' validation is not yet implemented");
		}
		return result;
	}
	
	private void setupColumn(HashMap<Integer, DataType> returnMap, DataType dt, int column, String[] header, String expectedName) throws IOException
	{
		if (column >= header.length)
		{
			error("Missing expected column " + column + " which should be '" + expectedName + "'");
		}
		else if (expectedName.length() > 0 && !expectedName.equals(header[column]))
		{
			error("Column " + (column + 1) + " should be '" + expectedName + "' but it is '" + header[column] + "'.");
		}
		returnMap.put(column, dt);
	}
	
	private void checkLineReturn(File f) throws IOException
	{
		InputStreamReader isr = new InputStreamReader(new BOMInputStream(new FileInputStream(f)), "UTF-8");
		Character lastCharMinus1 = null;
		Character lastChar = null;
		if (isr.ready())
		{
			lastCharMinus1 = (char)isr.read();
		}
		while (isr.ready())
		{
			if (lastChar != null)
			{
				lastCharMinus1 = lastChar;
			}
			lastChar = (char)isr.read();
			if (lastChar == '\n')
			{
				break;
			}
		}
		
		if (!(lastCharMinus1 == '\r' && lastChar == '\n'))
		{
			error("Files are supposed to have windows style line feeds - CR+LF");
		}
		
		//read to the end....
		char[] buffer = new char[10000];
		int read = isr.read(buffer);
		while (read != -1)
		{
			if (read == 1)
			{
				lastCharMinus1 = lastChar;
				lastChar = buffer[read - 1]; 
			}
			else if (read > 1)
			{
				lastChar = buffer[read - 1];
				lastCharMinus1 = buffer[read - 2];
			}
			read = isr.read(buffer);
		}
		if (!(lastCharMinus1 == '\r' && lastChar == '\n'))
		{
			error("Files are supposed to end with a windows style line feed - CR+LF");
		}
		
		isr.close();
	}
	
	private int checkPart(String part, int partNo, FileInfo fi) throws IOException
	{
		if (StringUtils.isBlank(part))
		{
			error("All 5 elements of the file name are required.  Part " + partNo + " is missing");
		}
		
		//In the cases where there are more parts than we expect, try to find the right part number
		int actualPartNumber = -1;
		
		try
		{
			if (part.matches("(z|x)?(sct|der|res|tls|doc)(1|2)?"))
			{
				actualPartNumber = 1;
				fi.setFileType(part);
				if (!part.matches("(z|x)?(sct|der)(1|2)"))
				{
					error("File types of sct and der must have a part 1 that ends with '1' or '2'.");
				}
			}
			else if (part.matches("(Concept|Description|Relationship|Identifier)|([csi]+Refset)") 
					|| "res".equals(fi.getFileTypeCode()) && partNo == 2)
			{
				//2nd part of the if statement isn't well specified in the spec - - could be nearly anything....
				fi.setContentType(part);
				actualPartNumber = 2;
				if (part.length() > 48)
				{
					error("Part two only allows 48 characters - this one is " + part.length() + " long. - '" + part + "'");
				}
			}
			else if ((fi.getFileTypeCode().matches("(sct)|(der)|(res)") && part.matches(".*(Full|Snapshot|Delta)((\\-[a-z]{2})(\\-[A-Z]{2})?)?"))
					|| (fi.getFileTypeCode().matches("(tls)|(doc)") && part.matches(".*(Current|Draft|Review)((\\-[a-z]{2})(\\-[A-Z]{2})?)?"))
					|| (fi.getFileTypeCode().equals("tls") && part.matches(".*((\\-[a-z]{2})(\\-[A-Z]{2})?)?")))
			{
				fi.setContentSubType(part);
				actualPartNumber = 3;
				if (part.length() > 48)
				{
					error("Part three only allows 48 characters - this one is " + part.length() + " long. - '" + part + "'");
				}
			}
			else if (part.matches("(INT|[A-Z]{2})?([0-9]{7})?"))
			{
				fi.setCountryNamespace(part);
				actualPartNumber = 4;
			}
			else if (part.matches("[0-9]{8}?"))
			{
				fi.setVersionDate(part);
				actualPartNumber = 5;
			}
			else if (!"res".equals(fi.getFileTypeCode()) && part.matches("StatedRelationship"))
			{
				//A specific bug case in the current RF2 export
				actualPartNumber = 2;
				error("The specification for part 2 doesn't allow 'StatedRelationship' when the type of part 1 is not 'res'");
			}
			else
			{
				error("Unknown part: " + part);
				actualPartNumber = -1;
			}
		}
		catch (UnsupportedOperationException e)
		{
			error("Parts of the filename were invald, and could not be identified correctly: " + e.getMessage());
		}
		
		if (actualPartNumber > 0 && actualPartNumber != partNo)
		{
			error("Part passed in as part " + partNo + " actually validates as part " + actualPartNumber);
		}
		
		
		//part three can have a hyphen between en-US, and can also have a hyphen before 'en' if it contains a doc status, etc.
		//otherwise, only alphanumeric is allowed
		if (!part.matches((actualPartNumber == 3 ? "[A-Za-z0-9]+((\\-[a-z]{2})(\\-[A-Z]{2})?)?" : "[A-Za-z0-9]+")))
		{
			error("The filename contains illegal characters in part " + partNo + " '" + part + "'");
		}
		
		return actualPartNumber;
	}
	
	private void startFile(File f) throws IOException
	{
		writeLine("Processing File " + f.getCanonicalPath().substring(inputFile.getCanonicalPath().length() + 1));
	}
	
	private void error(String message) throws IOException
	{
		errorCounter++;
		writeLine("ERROR: " + message);
	}
	
	private void writeLine(String line) throws IOException
	{
		ConsoleUtil.println(line);
		outputFile.write(line);
		outputFile.newLine();
	}

	public static void main(String[] args) throws MojoExecutionException, MojoFailureException
	{
		RF2ValidatorMojo i = new RF2ValidatorMojo();
		i.outputDirectory = new File("../rf2-validator-config/target");
		//i.inputFile = new File("../rf2-validator-config/target/generated-resources/data/SnomedCT_Release_US1000161_20130731/RF2Release/Full/Terminology");
		i.inputFile = new File("../rf2-validator-config/target/generated-resources/data/SnomedCT_Release_US1000161_20130731");
		i.execute();
	}
}
