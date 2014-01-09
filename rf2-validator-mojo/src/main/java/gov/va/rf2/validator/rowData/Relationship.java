package gov.va.rf2.validator.rowData;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;
import org.ihtsdo.tk.Ts;
import org.ihtsdo.tk.api.relationship.RelationshipVersionBI;
import org.ihtsdo.tk.binding.snomed.Snomed;

public class Relationship extends ValidatorBase
{
	long id_;
	Date effectiveTime_, expectedEffectiveTime_;
	boolean active_;
	long moduleId_;
	long conceptId_;
	long destinationId_;
	int relationshipGroup_;
	long typeId_;
	long characteristicTypeId_;
	long modifierId_;
	
	public Relationship(Object[] data, Date expectedEffectiveTime, HashMap<Long, UUID> sctToUUIDMap)
	{
		id_ = (long)data[0];
		effectiveTime_ = (Date)data[1];
		active_ = (boolean)data[2];
		moduleId_ = (long)data[3];
		conceptId_ = (long)data[4];
		destinationId_ = (long)data[5];
		relationshipGroup_ = (int)data[6];
		typeId_ = (long)data[7];
		characteristicTypeId_ = (long)data[8];
		modifierId_ = (long)data[9];
		expectedEffectiveTime_ = expectedEffectiveTime;
		sctToUUIDMap_ = sctToUUIDMap;
	}
	
	@SuppressWarnings("rawtypes")
	public void validate() throws Exception
	{
		ArrayList<String> errors = new ArrayList<>();

		RelationshipVersionBI rv = (RelationshipVersionBI)lookupComponentBySCTID(id_);
		
		if (expectedEffectiveTime_.getTime() != effectiveTime_.getTime())
		{
			errors.add("Wrong time - expected " + expectedEffectiveTime_.toString() + " but file has " + effectiveTime_.toString());
		}
		
		try
		{
			checkStatus(rv, active_);
		}
		catch (Exception e)
		{
			errors.add(e.getMessage());
		}
		
		try
		{
			checkModule(rv, moduleId_);
		}
		catch (Exception e)
		{
			errors.add(e.getMessage());
		}
		try
		{
			lookupConceptBySCTID(conceptId_);
		}
		catch (Exception e)
		{
			errors.add(e.getMessage());
		}
		
		try
		{
			lookupConceptBySCTID(destinationId_);
		}
		catch (Exception e)
		{
			errors.add(e.getMessage());
		}
		
		//relationshipGroup
		if (rv.getGroup() != relationshipGroup_)
		{
			errors.add("Wrong relationshipGroup - expected " + rv.getGroup() + " but file had " + relationshipGroup_);
		}
		
		//typeId
		if (!Ts.get().getComponent(rv.getTypeNid()).getPrimUuid().equals(lookupConceptBySCTID(typeId_).getPrimUuid()))
		{
			errors.add("Wrong typeId - expected the SCTID for " + Ts.get().getComponent(rv.getTypeNid()).getPrimUuid() + " but file has " + typeId_);
		}
		
		//characteristicTypeId
		if (!Ts.get().getComponent(rv.getCharacteristicNid()).getPrimUuid().equals(lookupConceptBySCTID(characteristicTypeId_).getPrimUuid()))
		{
			errors.add("Wrong CharacteristicId - expected the SCTID for " + Ts.get().getComponent(rv.getTypeNid()).getPrimUuid() + " but file has " + characteristicTypeId_);
		}
		
		//modifierId - doesn't exist in WB, as far as I see - looks to be hardcoded to SOME
		if (!Snomed.SOME.getUuids()[0].equals(lookupConceptBySCTID(modifierId_).getPrimUuid()))
		{
			errors.add("Wrong ModifierId - expected the SCTID for " + Snomed.SOME.getUuids()[0] + " but file has " + modifierId_);
		}
		
		throwErrors(errors);
	}
}
