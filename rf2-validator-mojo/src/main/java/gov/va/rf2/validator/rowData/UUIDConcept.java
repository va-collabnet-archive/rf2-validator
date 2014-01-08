package gov.va.rf2.validator.rowData;

import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;
import org.ihtsdo.tk.Ts;
import org.ihtsdo.tk.api.conceptattribute.ConceptAttributeVersionBI;

public class UUIDConcept extends ValidatorBase
{
	UUID id_;
	Date effectiveTime_, expectedEffectiveTime_;
	UUID active_;
	UUID moduleId_;
	boolean definitionStatusId_;
	
	public UUIDConcept(Object[] data, Date expectedEffectiveTime)
	{
		id_ = (UUID)data[0];
		effectiveTime_ = (Date)data[1];
		active_ = (UUID)data[2];
		moduleId_ = (UUID)data[3];
		definitionStatusId_ = (boolean)data[4];
		expectedEffectiveTime_ = expectedEffectiveTime;
	}
	
	@SuppressWarnings("rawtypes")
	public void validate() throws Exception
	{
		ArrayList<String> errors = new ArrayList<>();
		ConceptAttributeVersionBI cab = lookupConceptByUUID(id_);
		if (!Ts.get().getComponent(cab.getModuleNid()).getPrimUuid().equals(moduleId_))
		{
			errors.add("Wrong module - expected " + Ts.get().getComponent(cab.getModuleNid()).getPrimUuid() + " but file has " + moduleId_);
		}
		if (expectedEffectiveTime_.getTime() != effectiveTime_.getTime())
		{
			errors.add("Wrong time - expected " + expectedEffectiveTime_.toString() + " but file has " + effectiveTime_.toString());
		}
		if (!Ts.get().getComponent(cab.getStatusNid()).getPrimUuid().equals(active_))
		{
			errors.add("Wrong status - expected " + Ts.get().getComponent(cab.getStatusNid()).getPrimUuid() + " but file has " + active_);
		}
		if (cab.isDefined() != definitionStatusId_)
		{
			errors.add("Wrong definition status - expected " + cab.isDefined() + " but file has " + definitionStatusId_);
		}
		
		throwErrors(errors);
	}
}
