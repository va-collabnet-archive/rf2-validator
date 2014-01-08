package gov.va.rf2.validator.rowData;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;
import org.ihtsdo.tk.api.conceptattribute.ConceptAttributeVersionBI;

public class Concept extends ValidatorBase
{
	Long id_;
	Date effectiveTime_, expectedEffectiveTime_;
	boolean active_;
	long moduleId_;
	long definitionStatusId_;

	public Concept(Object[] data, Date expectedEffectiveTime, HashMap<Long, UUID> sctToUUIDMap)
	{
		id_ = (long) data[0];
		effectiveTime_ = (Date) data[1];
		active_ = (boolean) data[2];
		moduleId_ = (long) data[3];
		definitionStatusId_ = (long) data[4];
		expectedEffectiveTime_ = expectedEffectiveTime;
		sctToUUIDMap_ = sctToUUIDMap;
	}

	public void validate() throws Exception
	{
		
		ArrayList<String> errors = new ArrayList<>();
		ConceptAttributeVersionBI<?> cav = lookupConceptBySCTID(id_);
		
		try
		{
			checkModule(cav, moduleId_);
		}
		catch (Exception e)
		{
			errors.add(e.getMessage());
		}

		if (expectedEffectiveTime_.getTime() != effectiveTime_.getTime())
		{
			errors.add("Wrong time - expected " + expectedEffectiveTime_.toString() + " but file has " + effectiveTime_.toString());
		}
		try
		{
			checkStatus(cav, active_);
		}
		catch (Exception e)
		{
			errors.add(e.getMessage());
		}
		
		if (definitionStatusId_ == (cav.isDefined() ? 900000000000073002l : 900000000000074008l))
		{
			errors.add("Wrong definition status - expected " + (cav.isDefined() ? 900000000000073002l : 900000000000074008l) + " but file has " + definitionStatusId_);
		}

		throwErrors(errors);
	}
}
