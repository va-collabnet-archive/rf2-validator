package gov.va.rf2.validator.rowData;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;
import org.ihtsdo.tk.api.ComponentVersionBI;

public class Refset extends ValidatorBase
{
	UUID id_;
	Date effectiveTime_, expectedEffectiveTime_;
	boolean active_;
	long moduleId_;
	long refsetId_;
	long referencedComponentId1_;
	UUID referencedComponentId2_;
	ArrayList<Object> otherFields_ = new ArrayList<>();
	String[] otherFieldNames_;

	public Refset(Object[] data, Date expectedEffectiveTime, HashMap<Long, UUID> sctToUUIDMap, String[] otherFieldNames)
	{
		id_ = (UUID) data[0];
		effectiveTime_ = (Date) data[1];
		active_ = (boolean) data[2];
		moduleId_ = (long) data[3];
		refsetId_ = (long) data[4];
		if (data[5] instanceof UUID)
		{
			referencedComponentId2_ = (UUID) data[5];
		}
		else
		{
			referencedComponentId1_ = (long) data[5];
		}
		expectedEffectiveTime_ = expectedEffectiveTime;
		for (int i = 6; i < data.length; i++)
		{
			otherFields_.add(data[i]);
		}
		otherFieldNames_ = otherFieldNames;
		sctToUUIDMap_ = sctToUUIDMap;
	}

	public void validate() throws Exception
	{
		ArrayList<String> errors = new ArrayList<>();

		ComponentVersionBI cv = lookupComponentByUUID(id_);

		if (expectedEffectiveTime_.getTime() != effectiveTime_.getTime())
		{
			errors.add("Wrong time - expected " + expectedEffectiveTime_.toString() + " but file has " + effectiveTime_.toString());
		}

		try
		{
			checkStatus(cv, active_);
		}
		catch (Exception e)
		{
			errors.add(e.getMessage());
		}

		try
		{
			checkModule(cv, moduleId_);
		}
		catch (Exception e)
		{
			errors.add(e.getMessage());
		}

		try
		{
			lookupComponentBySCTID(refsetId_);
		}
		catch (Exception e)
		{
			errors.add("Couldn't locate specified refset - " + e.getMessage());
		}

		try
		{
			if (referencedComponentId2_ == null)
			{
				lookupComponentBySCTID(referencedComponentId1_);
			}
			else
			{
				lookupComponentByUUID(referencedComponentId2_);
			}
		}
		catch (Exception e)
		{
			errors.add("Couldn't locate the member referenced by the refset " + (referencedComponentId2_ == null ? referencedComponentId1_ : referencedComponentId2_)
					+ " " + e.getMessage());
		}

		int i = 6;
		for (Object o : otherFields_)
		{
			try
			{
				checkExtensionField(o, otherFieldNames_[i++]);
			}
			catch (Exception e)
			{
				errors.add("Failed validating extension field " + o + ": " + e.getMessage());
			}
		}
		throwErrors(errors);
	}
}
