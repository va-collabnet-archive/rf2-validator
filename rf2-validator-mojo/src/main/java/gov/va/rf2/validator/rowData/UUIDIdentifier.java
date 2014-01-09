package gov.va.rf2.validator.rowData;

import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;
import org.ihtsdo.tk.Ts;
import org.ihtsdo.tk.api.ComponentVersionBI;

public class UUIDIdentifier extends ValidatorBase
{
	UUID identifierSchemeId_;
	String alternateIdentifier_;
	Date effectiveTime_, expectedEffectiveTime_;
	UUID active_;
	UUID moduleId_;
	UUID referencedComponentId_;
	
	private final UUID uuidIdScheme = UUID.fromString("680f3f6c-7a2a-365d-b527-8c9a96dd1a94");
	
	public UUIDIdentifier(Object[] data, Date expectedEffectiveTime)
	{
		identifierSchemeId_ = (UUID)data[0];
		alternateIdentifier_ = (String)data[1];
		effectiveTime_ = (Date)data[2];
		active_ = (UUID)data[3];
		moduleId_ = (UUID)data[4];
		referencedComponentId_ = (UUID)data[5];
		expectedEffectiveTime_ = expectedEffectiveTime;
	}
	
	public void validate() throws Exception
	{
		ArrayList<String> errors = new ArrayList<>();
		if (!uuidIdScheme.equals(identifierSchemeId_))
		{
			throw new Exception("Validator doesn't support the identifier scheme " + identifierSchemeId_);
		}
		
		ComponentVersionBI cv = lookupComponentByUUID(referencedComponentId_);
		
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
		
		if (!referencedComponentId_.equals(alternateIdentifier_))
		{
			try
			{
				lookupComponentByUUID(UUID.fromString(alternateIdentifier_));
			}
			catch (Exception e)
			{
				errors.add(e.getMessage());
			}
		}
		
		throwErrors(errors);
	}
}
