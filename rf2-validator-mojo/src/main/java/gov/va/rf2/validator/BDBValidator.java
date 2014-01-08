package gov.va.rf2.validator;

import gov.va.oia.terminology.converters.sharedUtils.ConsoleUtil;
import java.io.File;
import org.ihtsdo.db.bdb.Bdb;

public class BDBValidator
{

	public BDBValidator(File dbPath)
	{
		Bdb.setup(dbPath.getAbsolutePath());
		
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					Bdb.close();
				}
				catch (Exception ex)
				{
					ex.printStackTrace();
				}
			}
		}, "BdbTestRunner Shutdown hook"));
		
	}
	
	
	public void shutdown()
	{
		try
		{
			Bdb.close();
		}
		catch (Exception e)
		{
			ConsoleUtil.printErrorln("Error shutting down DB: " + e);
		}
	}
}
