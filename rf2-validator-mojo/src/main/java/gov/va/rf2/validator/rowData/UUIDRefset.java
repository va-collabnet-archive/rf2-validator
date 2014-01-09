package gov.va.rf2.validator.rowData;

import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;
import org.ihtsdo.tk.Ts;
import org.ihtsdo.tk.api.ComponentVersionBI;

public class UUIDRefset extends ValidatorBase
{
	UUID id_;
	Date effectiveTime_, expectedEffectiveTime_;
	UUID active_;
	UUID moduleId_;
	UUID refsetId_;
	long referencedComponentId1_;
	UUID referencedComponentId2_;
	ArrayList<Object> otherFields_ = new ArrayList<>();
	String[] otherFieldNames_;
	

	public UUIDRefset(Object[] data, Date expectedEffectiveTime, String[] otherFieldNames)
	{
		id_ = (UUID) data[0];
		effectiveTime_ = (Date) data[1];
		active_ = (UUID) data[2];
		moduleId_ = (UUID) data[3];
		refsetId_ = (UUID) data[4];
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
	}

	public void validate() throws Exception
	{
		ArrayList<String> errors = new ArrayList<>();

		ComponentVersionBI cv = lookupComponentByUUID(id_);

		if (expectedEffectiveTime_.getTime() != effectiveTime_.getTime())
		{
			errors.add("Wrong time - expected " + expectedEffectiveTime_.toString() + " but file has " + effectiveTime_.toString());
		}

		if (!Ts.get().getComponent(cv.getStatusNid()).getPrimUuid().equals(active_))
		{
			errors.add("Wrong status - expected " + Ts.get().getComponent(cv.getStatusNid()).getPrimUuid() + " but file has " + active_);
		}

		if (!Ts.get().getComponent(cv.getModuleNid()).getPrimUuid().equals(moduleId_))
		{
			errors.add("Wrong module - expected " + Ts.get().getComponent(cv.getModuleNid()).getPrimUuid() + " but file has " + moduleId_);
		}

		try
		{
			lookupComponentByUUID(refsetId_);
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
