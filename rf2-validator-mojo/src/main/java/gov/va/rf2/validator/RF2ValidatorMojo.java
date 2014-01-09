package gov.va.rf2.validator;

import gov.va.oia.terminology.converters.sharedUtils.ConsoleUtil;
import gov.va.rf2.validator.rowData.Concept;
import gov.va.rf2.validator.rowData.Description;
import gov.va.rf2.validator.rowData.Identifier;
import gov.va.rf2.validator.rowData.Refset;
import gov.va.rf2.validator.rowData.Relationship;
import gov.va.rf2.validator.rowData.UUIDConcept;
import gov.va.rf2.validator.rowData.UUIDDescription;
import gov.va.rf2.validator.rowData.UUIDIdentifier;
import gov.va.rf2.validator.rowData.UUIDRefset;
import gov.va.rf2.validator.rowData.UUIDRelationship;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import au.com.bytecode.opencsv.CSVReader;

/**
 * Goal which validates a set of RF2 output files.
 * 
 * This validator validates all aspects of the RF2 file naming, reporting on any file name which is inconsistent
 * with the TIG file naming conventions.
 * 
 * Next, the validator checks the content of each of the .txt files, ensuring that the proper EOL characters are used,
 * the proper number of EOL characters are used, the content is parseable as UFT-8.
 * 
 * It checks that each file contains a header, with the proper header columns as specified in the TIG.
 * It checks that each data row is consistent with the header.
 * It checks that each column in each row of data contains data of the proper format, per the TIG (SCTID, boolean, etc)
 * 
 * Finally, there is an optional step, where an inputDb can be provided - in which case, every identifier in the output
 * files is looked up in the db to ensure that it exists, and that all other columns associated with the ID are consistent
 * with the data found in the DB.
 * 
 * DB consistency issues are written to a separate report.
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
	private File inputRF2;

	/**
	 * Expected 'effectiveTime' within the RF2 export files. Should be formatted as yyyyMMdd.
	 * 
	 * @parameter
	 * @required
	 */
	private String expectedEffectiveTime;

	/**
	 * Location of the BDB database folder (that was the source of the RF2 export). Expected to be a directory. Optional
	 * 
	 * @parameter
	 * @optional
	 */
	private File inputDb;

	private BufferedWriter outputFile;
	private BufferedWriter dbLookupOutputFile;

	// YYYYMMDD
	SimpleDateFormat sdf1 = new SimpleDateFormat("yyyyMMdd");

	// YYYYMMDDThhmmssZ
	// Note - the pattern requires java 1.7
	SimpleDateFormat sdf2 = new SimpleDateFormat("yyyyMMdd'T'HHmmssX");

	private int dbLookupErrorCounterPerFile = 0;
	private int errorCounter = 0;
	private int fileCounter = 0;
	private int validFileCounter = 0;
	private BDBValidator bdbValidator;
	private Date expectedEffectiveTime_;
	private SCTUUIDMaps maps_;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException
	{
		try
		{
			if (!outputDirectory.exists())
			{
				outputDirectory.mkdirs();
			}

			if (!inputRF2.exists() || !inputRF2.isDirectory())
			{
				throw new MojoExecutionException("The parameter 'inputRF2' must point to an existing folder.  Currently set to: " + inputRF2);
			}

			try
			{
				expectedEffectiveTime_ = sdf1.parse(expectedEffectiveTime);
			}
			catch (ParseException e)
			{
				throw new MojoExecutionException("The parameter 'expectedEffectiveTime' must be set to a yyyyMMdd value");
			}

			outputFile = new BufferedWriter(new FileWriter(new File(outputDirectory, "formattingReport.txt")));
			ConsoleUtil.println("Validating RF2 Export");

			if (inputDb != null && inputDb.exists() && inputDb.isDirectory())
			{
				ConsoleUtil.println("Initializing Database");
				bdbValidator = new BDBValidator(inputDb);
				dbLookupOutputFile = new BufferedWriter(new FileWriter(new File(outputDirectory, "dbLookupReport.txt")));
			}

			maps_ = new SCTUUIDMaps(inputRF2);

			processFolder(inputRF2);

			writeLine("Processed " + fileCounter + " files, " + validFileCounter + " were valid, " + (fileCounter - validFileCounter) + " had errors", false);

			outputFile.close();

			if (bdbValidator != null)
			{
				ConsoleUtil.println("Closing Database");
				bdbValidator.shutdown();
				dbLookupOutputFile.close();
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new MojoExecutionException("oops", e);
		}
	}

	private void processFolder(File folder) throws Exception
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
				if (f.getName().startsWith("sct2_to_uuid_map"))
				{
					error("sct2_to_uuid_map files don't yet align to TIG naming conventions");
					fi.setContentType("-MAP-");  // not part of the TIG
					fi.setExtension(f.getName().substring(f.getName().lastIndexOf('.')).toLowerCase());
				}
				else if (f.getName().toLowerCase().matches("[a-zA-Z0-9\\-_]+\\.[a-zA-Z0-9]{1,4}"))
				{
					if (f.getName().length() > 128)
					{
						error("Max file name length should be 128 characters - this file is " + f.getName().length());
					}

					if (f.getName().contains("UUID"))
					{
						fi.setIsUUIDFile(true);
					}

					String[] nameParts = f.getName().substring(0, f.getName().length() - 4).split("_");
					if (nameParts.length != 5)
					{
						error("Invalid number of elements in the file name.  Expected 5, had " + nameParts.length);

						// Hack code for intermediate export files which don't currently follow convention..
						if (nameParts.length > 5 && f.getName().contains("UUID_"))
						{
							String temp = f.getName().substring(0, f.getName().length() - 4);
							temp = temp.replace("UUID_", "UUID");
							nameParts = temp.split("_");
						}
					}

					int partNo = 1;
					boolean[] foundParts = new boolean[5];
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
								Object[] parsedData = validateRow(row, columnInfo, lineNo);
								try
								{
									if (bdbValidator != null)
									{
										if (fi.getContentType().equals("Concept"))
										{
											if (fi.getIsUUIDFile())
											{
												new UUIDConcept(parsedData, expectedEffectiveTime_).validate();
											}
											else
											{
												new Concept(parsedData, expectedEffectiveTime_, maps_.getMap(fi)).validate();
											}
										}
										else if (fi.getContentType().equals("Description"))
										{
											if (fi.getIsUUIDFile())
											{
												new UUIDDescription(parsedData, expectedEffectiveTime_).validate();
											}
											else
											{
												new Description(parsedData, expectedEffectiveTime_, maps_.getMap(fi)).validate();
											}
										}
										else if (fi.getContentType().matches("(Relationship)|(StatedRelationship)"))
										{
											if (fi.getIsUUIDFile())
											{
												new UUIDRelationship(parsedData, expectedEffectiveTime_).validate();
											}
											else
											{
												new Relationship(parsedData, expectedEffectiveTime_, maps_.getMap(fi)).validate();
											}
										}
										else if (fi.getContentType().equals("Identifier"))
										{
											if (fi.getIsUUIDFile())
											{
												new UUIDIdentifier(parsedData, expectedEffectiveTime_).validate();
											}
											else
											{
												new Identifier(parsedData, expectedEffectiveTime_, maps_.getMap(fi)).validate();
											}
										}
										else if (fi.getContentType().endsWith("Refset"))
										{
											if (fi.getIsUUIDFile())
											{
												new UUIDRefset(parsedData, expectedEffectiveTime_, (header.length > 5 ? Arrays.copyOfRange(header, 6, header.length)
														: new String[] {})).validate();
											}
											else
											{
												new Refset(parsedData, expectedEffectiveTime_, maps_.getMap(fi), (header.length > 5 ? Arrays.copyOfRange(header, 6,
														header.length) : new String[] {})).validate();
											}
										}
									}
								}
								catch (Exception e)
								{
									dbLookupError("Line " + lineNo + " failed the lookup in the DB: " + e.getMessage());
								}
							}
							row = r.readNext();
							lineNo++;
						}
					}
					r.close();
				}
				writeLine("", false);
				writeLine("", true);
				fileCounter++;
				if (errorCounter == startErrorCount)
				{
					validFileCounter++;
				}
			}
		}
	}

	private Object[] validateRow(String[] row, HashMap<Integer, DataType> columnInfo, int lineNo) throws IOException
	{
		Object[] parsedData = new Object[row.length];

		for (int i = 0; i < row.length; i++)
		{
			try
			{
				parsedData[i] = parseData(row[i], columnInfo.get(i));
			}
			catch (Exception e)
			{
				error("Data on line " + lineNo + " column " + (i + 1) + " is illegal - " + e.getMessage());
			}
		}
		return parsedData;
	}

	private Object parseData(String data, DataType dataType) throws Exception
	{
		if (dataType == null)
		{
			throw new Exception("malformed data file, unknown what the column should be");
		}
		else
		{
			switch (dataType)
			{
				case Integer:
					try
					{
						return Integer.parseInt(data);
					}
					catch (NumberFormatException e)
					{
						throw new Exception("should be an Integer");
					}
				case Boolean:
					if (!(data.equals("0") || data.equals("1")))
					{
						throw new Exception("should be '0' (false) or '1' (true)");
					}
					return data.equals("1");
				case SCTID:
					try
					{
						return checkSCTID(data);
					}
					catch (Exception e)
					{
						throw new Exception("should be a SCTID");
					}
				case SCTIDorUUID:
					try
					{
						if (data.length() == 36)
						{
							return UUID.fromString(data);
						}
						else
						{
							return checkSCTID(data);
						}
					}
					catch (Exception e)
					{
						throw new Exception("should be a SCTID or UUID");
					}
				case String:
					if (data.length() == 0)
					{
						throw new Exception("No data found");
					}
					return data;
				case Time:
					try
					{
						if (data.length() == 8)
						{
							return sdf1.parse(data);
						}
						else
						{
							return sdf2.parse(data);
						}
					}
					catch (Exception e)
					{
						throw new Exception("unparsable time value");
					}
				case UUID:
					try
					{
						return UUID.fromString(data);
					}
					catch (Exception e)
					{
						throw new Exception("should be a UUID");
					}
				case UUIDBoolean:
					if (!data.toLowerCase().equals("true") && !data.toLowerCase().equals("false"))
					{
						throw new Exception("should be 'true' or 'false'");
					}
					return data.toLowerCase().equals("true");
				default:
					throw new Exception("Unhandeled data type");
			}
		}
	}

	public long checkSCTID(String sctId) throws Exception
	{
		if (sctId.length() < 6 || sctId.length() > 18)
		{
			throw new Exception("Invalid SCTID length " + sctId);
		}

		Long l;
		try
		{
			l = Long.parseLong(sctId);
		}
		catch (NumberFormatException e)
		{
			throw new Exception("SCTID should be a number");
		}

		VerhoeffDihedralCheck.validateCheckDigit(sctId);
		String partition = sctId.substring(sctId.length() - 3, sctId.length() - 1);

		if (!partition.matches("(0[0-5])|(1[0-5])"))
		{
			throw new Exception("Invalid partition portion of SCTID '" + partition + "'");
		}
		return l;
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
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.SCTID), 0, header, "id");
			setupColumn(result, DataType.Time, 1, header, "effectiveTime");
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.Boolean), 2, header, "active");
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.SCTID), 3, header, "moduleId");
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUIDBoolean : DataType.SCTID), 4, header, "definitionStatusId");
			if (header.length > 5)
			{
				error("Too many columns - should have 5, but has " + header.length);
			}
		}
		else if (fi.getContentType().equals("Description"))
		{
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.SCTID), 0, header, "id");
			setupColumn(result, DataType.Time, 1, header, "effectiveTime");
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.Boolean), 2, header, "active");
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.SCTID), 3, header, "moduleId");
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.SCTID), 4, header, "conceptId");
			setupColumn(result, DataType.String, 5, header, "languageCode");
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.SCTID), 6, header, "typeId");
			setupColumn(result, DataType.String, 7, header, "term");
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUIDBoolean : DataType.SCTID), 8, header, "caseSignificanceId");
			if (header.length > 9)
			{
				error("Too many columns - should have 9, but has " + header.length);
			}
		}
		else if (fi.getContentType().equals("Relationship"))
		{
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.SCTID), 0, header, "id");
			setupColumn(result, DataType.Time, 1, header, "effectiveTime");
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.Boolean), 2, header, "active");
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.SCTID), 3, header, "moduleId");
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.SCTID), 4, header, "sourceId");
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.SCTID), 5, header, "destinationId");
			setupColumn(result, DataType.Integer, 6, header, "relationshipGroup");
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.SCTID), 7, header, "typeId");
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.SCTID), 8, header, "characteristicTypeId");
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.SCTID), 9, header, "modifierId");
			if (header.length > 10)
			{
				error("Too many columns - should have 10, but has " + header.length);
			}
		}
		else if (fi.getContentType().equals("Identifier"))
		{
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.SCTID), 0, header, "identifierSchemeId");
			setupColumn(result, DataType.String, 1, header, "alternateIdentifier");
			setupColumn(result, DataType.Time, 2, header, "effectiveTime");
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.Boolean), 3, header, "active");
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.SCTID), 4, header, "moduleId");
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.SCTID), 5, header, "referencedComponentId");
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
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.Boolean), 2, header, "active");
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.SCTID), 3, header, "moduleId");
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.SCTID), 4, header, "refsetId");
			setupColumn(result, DataType.SCTIDorUUID, 5, header, "referencedComponentId");

			for (int i = 0; i < prefix.length(); i++)
			{
				char c = prefix.charAt(i);
				if (c == 'c')
				{
					setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.SCTID), 6 + i, header, "");
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
		// 99% sure that this is supposed to be the same as Relationship - but the TIG doesn't specify
		else if (fi.getContentType().equals("StatedRelationship"))
		{
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.SCTID), 0, header, "id");
			setupColumn(result, DataType.Time, 1, header, "effectiveTime");
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.Boolean), 2, header, "active");
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.SCTID), 3, header, "moduleId");
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.SCTID), 4, header, "sourceId");
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.SCTID), 5, header, "destinationId");
			setupColumn(result, DataType.Integer, 6, header, "relationshipGroup");
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.SCTID), 7, header, "typeId");
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.SCTID), 8, header, "characteristicTypeId");
			setupColumn(result, (fi.getIsUUIDFile() ? DataType.UUID : DataType.SCTID), 9, header, "modifierId");
			if (header.length > 10)
			{
				error("Too many columns - should have 10, but has " + header.length);
			}
		}
		// Not part of the tig
		else if (fi.getContentType().equals("-MAP-"))
		{
			setupColumn(result, DataType.SCTID, 0, header, "sctId");
			setupColumn(result, DataType.UUID, 1, header, "uuid");
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
			lastCharMinus1 = (char) isr.read();
		}
		while (isr.ready())
		{
			if (lastChar != null)
			{
				lastCharMinus1 = lastChar;
			}
			lastChar = (char) isr.read();
			if (lastChar == '\n')
			{
				break;
			}
		}

		if (!(lastCharMinus1 == '\r' && lastChar == '\n'))
		{
			error("Files are supposed to have windows style line feeds - CR+LF");
		}

		// read to the end....
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

		// In the cases where there are more parts than we expect, try to find the right part number
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
			else if (part.matches("(Concept|Description|Relationship|Identifier)|([csi]+Refset)") || "res".equals(fi.getFileTypeCode()) && partNo == 2)
			{
				// 2nd part of the if statement isn't well specified in the spec - - could be nearly anything....
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
				// A specific bug case in the current RF2 export
				actualPartNumber = 2;
				fi.setContentType(part);
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

		// part three can have a hyphen between en-US, and can also have a hyphen before 'en' if it contains a doc status, etc.
		// otherwise, only alphanumeric is allowed
		if (!part.matches((actualPartNumber == 3 ? "[A-Za-z0-9]+((\\-[a-z]{2})(\\-[A-Z]{2})?)?" : "[A-Za-z0-9]+")))
		{
			error("The filename contains illegal characters in part " + partNo + " '" + part + "'");
		}

		return actualPartNumber;
	}

	private void startFile(File f) throws IOException
	{
		dbLookupErrorCounterPerFile = 0;
		writeLine("Processing File " + f.getCanonicalPath().substring(inputRF2.getCanonicalPath().length() + 1), false);
		writeLine("Processing File " + f.getCanonicalPath().substring(inputRF2.getCanonicalPath().length() + 1), true);
	}

	private void error(String message) throws IOException
	{
		errorCounter++;
		writeLine("ERROR: " + message, false);
	}

	private void dbLookupError(String message) throws IOException
	{
		dbLookupErrorCounterPerFile++;
		writeLine("ERROR: " + message, true);
	}

	private void writeLine(String line, boolean dbLookUpFailue) throws IOException
	{

		if (dbLookUpFailue && dbLookupOutputFile != null)
		{
			if (dbLookupErrorCounterPerFile < 10)
			{
				ConsoleUtil.println(line);
			}
			else if (dbLookupErrorCounterPerFile == 10)
			{
				ConsoleUtil.printErrorln("Too many DB lookup errors, further errors surpressed from console.  See dbLookupReport.txt");
			}
			dbLookupOutputFile.write(line);
			dbLookupOutputFile.newLine();
		}
		else
		{
			ConsoleUtil.println(line);
			outputFile.write(line);
			outputFile.newLine();
		}
	}

	public static void main(String[] args) throws MojoExecutionException, MojoFailureException
	{
		RF2ValidatorMojo i = new RF2ValidatorMojo();
		i.outputDirectory = new File("../rf2-validator-config/target");
		// i.inputRF2 = new
		// File("../rf2-validator-config/target/generated-resources/data/SnomedCT_Release_US1000161_20130731/RF2Release/Full/Terminology");
		// i.inputRF2 = new File("../rf2-validator-config/target/generated-resources/data/SnomedCT_Release_US1000161_20130731");
		i.inputRF2 = new File("../rf2-validator-config/target/generated-resources/RF2-data/");
		i.inputDb = new File("../rf2-validator-config/target/generated-resources/bdb-data/berkeley-db");
		i.expectedEffectiveTime = "20130731";
		i.execute();
	}
}
