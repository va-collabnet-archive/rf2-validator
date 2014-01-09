package gov.va.rf2.validator.rowData;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;
import org.ihtsdo.tk.api.ComponentVersionBI;

public class Identifier extends ValidatorBase
{
	long identifierSchemeId_;
	String alternateIdentifier_;
	Date effectiveTime_, expectedEffectiveTime_;
	boolean active_;
	long moduleId_;
	long referencedComponentId_;
	
	private final long uuidIdScheme = 900000000000002006l;
	
	public Identifier(Object[] data, Date expectedEffectiveTime, HashMap<Long, UUID> sctToUUIDMap)
	{
		identifierSchemeId_ = (long)data[0];
		alternateIdentifier_ = (String)data[1];
		effectiveTime_ = (Date)data[2];
		active_ = (boolean)data[3];
		moduleId_ = (long)data[4];
		referencedComponentId_ = (long)data[5];
		expectedEffectiveTime_ = expectedEffectiveTime;
		sctToUUIDMap_ = sctToUUIDMap;
	}
	
	public void validate() throws Exception
	{
		ArrayList<String> errors = new ArrayList<>();
		if (uuidIdScheme != identifierSchemeId_)
		{
			throw new Exception("Validator doesn't support the identifier scheme " + identifierSchemeId_);
		}
		
		ComponentVersionBI cv = lookupComponentBySCTID(referencedComponentId_);
		
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
			ComponentVersionBI cv2 = lookupComponentByUUID(UUID.fromString(alternateIdentifier_));
			if (!cv2.getPrimUuid().equals(cv.getPrimUuid()))
			{
				errors.add("Concept looked up by alternate identifier did not match concept looked up by primary identifier");
			}
		}
		catch (Exception e) 
		{
			errors.add(e.getMessage());
		}
		
		throwErrors(errors);
	}
}
