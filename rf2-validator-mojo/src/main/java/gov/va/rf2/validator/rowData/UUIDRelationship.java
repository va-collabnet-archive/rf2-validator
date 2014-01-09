package gov.va.rf2.validator.rowData;

import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;
import org.ihtsdo.tk.Ts;
import org.ihtsdo.tk.api.relationship.RelationshipVersionBI;
import org.ihtsdo.tk.binding.snomed.Snomed;

public class UUIDRelationship extends ValidatorBase
{
	UUID id_;
	Date effectiveTime_, expectedEffectiveTime_;
	UUID active_;
	UUID moduleId_;
	UUID conceptId_;
	UUID destinationId_;
	int relationshipGroup_;
	UUID typeId_;
	UUID characteristicTypeId_;
	UUID modifierId_;
	
	public UUIDRelationship(Object[] data, Date expectedEffectiveTime)
	{
		id_ = (UUID)data[0];
		effectiveTime_ = (Date)data[1];
		active_ = (UUID)data[2];
		moduleId_ = (UUID)data[3];
		conceptId_ = (UUID)data[4];
		destinationId_ = (UUID)data[5];
		relationshipGroup_ = (int)data[6];
		typeId_ = (UUID)data[7];
		characteristicTypeId_ = (UUID)data[8];
		modifierId_ = (UUID)data[9];
		expectedEffectiveTime_ = expectedEffectiveTime;
	}
	
	@SuppressWarnings("rawtypes")
	public void validate() throws Exception
	{
		ArrayList<String> errors = new ArrayList<>();

		RelationshipVersionBI rv = (RelationshipVersionBI)lookupComponentByUUID(id_);
		
		if (expectedEffectiveTime_.getTime() != effectiveTime_.getTime())
		{
			errors.add("Wrong time - expected " + expectedEffectiveTime_.toString() + " but file has " + effectiveTime_.toString());
		}

		if (!Ts.get().getComponent(rv.getStatusNid()).getPrimUuid().equals(active_))
		{
			errors.add("Wrong status - expected " + Ts.get().getComponent(rv.getStatusNid()).getPrimUuid() + " but file has " + active_);
		}
		
		if (!Ts.get().getComponent(rv.getModuleNid()).getPrimUuid().equals(moduleId_))
		{
			errors.add("Wrong module - expected " + Ts.get().getComponent(rv.getModuleNid()).getPrimUuid() + " but file has " + moduleId_);
		}
		
		lookupConceptByUUID(conceptId_);
		lookupConceptByUUID(destinationId_);
		
		//relationshipGroup
		if (rv.getGroup() != relationshipGroup_)
		{
			errors.add("Wrong relationshipGroup - expected " + rv.getGroup() + " but file had " + relationshipGroup_);
		}
		
		//typeId
		if (!Ts.get().getComponent(rv.getTypeNid()).getPrimUuid().equals(typeId_))
		{
			errors.add("Wrong typeId - expected " + Ts.get().getComponent(rv.getTypeNid()).getPrimUuid() + " but file has " + typeId_);
		}
		
		//characteristicTypeId
		if (!Ts.get().getComponent(rv.getCharacteristicNid()).getPrimUuid().equals(characteristicTypeId_))
		{
			errors.add("Wrong CharacteristicId - expected " + Ts.get().getComponent(rv.getTypeNid()).getPrimUuid() + " but file has " + characteristicTypeId_);
		}
		
		//modifierId - doesn't exist in WB, as far as I see - looks to be hardcoded to SOME
		if (!Snomed.SOME.getUuids()[0].equals(modifierId_))
		{
			errors.add("Wrong ModifierId - expected " + Snomed.SOME.getUuids()[0] + " but file has " + modifierId_);
		}
		
		throwErrors(errors);
	}
}
