package gov.va.rf2.validator;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.UUID;
import org.apache.commons.io.input.BOMInputStream;
import au.com.bytecode.opencsv.CSVReader;



public class SCTUUIDMaps
{
	
	private HashMap<Long, UUID> delta_;
	private HashMap<Long, UUID> full_;
	private HashMap<Long, UUID> snapshot_;

	public SCTUUIDMaps(File folder) throws Exception
	{
		processFolder(folder);
	}
	
	protected HashMap<Long, UUID> getMap(FileInfo fi)
	{
		if (fi.getContentSubType().contains("Delta"))
		{
			return delta_;
		}
		else if (fi.getContentSubType().contains("Snapshot"))
		{
			return snapshot_;
		}
		else if (fi.getContentSubType().contains("Full"))
		{
			return full_;
		}
		return null;
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
				if (f.getName().startsWith("sct2_to_uuid_map"))
				{
					HashMap<Long, UUID> map;
					if (f.getName().contains("Delta"))
					{
						if (delta_ != null)
						{
							throw new Exception("Found multiple Delta map files");
						}
						delta_ = new HashMap<>();
						map = delta_;
					}
					else if (f.getName().contains("Full"))
					{
						if (full_ != null)
						{
							throw new Exception("Found multiple Full map files");
						}
						full_ = new HashMap<>();
						map = full_;
					}
					else if (f.getName().contains("Snapshot"))
					{
						if (snapshot_ != null)
						{
							throw new Exception("Found multiple Snapshot map files");
						}
						snapshot_ = new HashMap<>();
						map = snapshot_;
					}
					else
					{
						throw new Exception("Unexpected mapping file");
					}
					
					CSVReader r = new CSVReader(new InputStreamReader(new BOMInputStream(new FileInputStream(f)), "UTF-8"), '\t');
					for (String[] row : r.readAll())
					{
						if (row.length > 0)
						{
							if (row[0].equals("sctId"))
							{
								//header
								continue;
							}
							map.put(Long.parseLong(row[0]), UUID.fromString(row[1]));
						}
					}
					r.close();
				}
			}
		}
	}
}