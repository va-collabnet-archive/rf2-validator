package gov.va.rf2.validator.rowData;

import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;
import org.ihtsdo.tk.Ts;
import org.ihtsdo.tk.api.ComponentVersionBI;
import org.ihtsdo.tk.api.description.DescriptionVersionBI;

public class UUIDDescription extends ValidatorBase
{
	UUID id_;
	Date effectiveTime_, expectedEffectiveTime_;
	UUID active_;
	UUID moduleId_;
	UUID conceptId_;
	String languageCode_;
	UUID typeId_;
	String term_;
	boolean caseSignificanceId_;
	
	public UUIDDescription(Object[] data, Date expectedEffectiveTime)
	{
		id_ = (UUID)data[0];
		effectiveTime_ = (Date)data[1];
		active_ = (UUID)data[2];
		moduleId_ = (UUID)data[3];
		conceptId_ = (UUID)data[4];
		languageCode_ = (String)data[5];
		typeId_ = (UUID)data[6];
		term_ = (String)data[7];
		caseSignificanceId_ = (boolean)data[8];
		expectedEffectiveTime_ = expectedEffectiveTime;
	}
	
	@SuppressWarnings("rawtypes")
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
		
		lookupConceptByUUID(conceptId_);
		
		//languageCode
		if (!((DescriptionVersionBI)cv).getLang().equals(languageCode_))
		{
			errors.add("Wrong languageCode - expected " + ((DescriptionVersionBI)cv).getLang() + " but file has " + languageCode_);
		}
		
		//typeId
		if (!Ts.get().getComponent(((DescriptionVersionBI)cv).getTypeNid()).getPrimUuid().equals(typeId_))
		{
			errors.add("Wrong typeId - expected " + Ts.get().getComponent(((DescriptionVersionBI)cv).getTypeNid()).getPrimUuid() + " but file has " + typeId_);
		}
		
		//term
		if (!((DescriptionVersionBI)cv).getText().equals(term_))
		{
			errors.add("Wrong term - expected " + ((DescriptionVersionBI)cv).getText() + " but file has " + term_);
		}
		
		//caseSig
		if (((DescriptionVersionBI)cv).isInitialCaseSignificant() != caseSignificanceId_)
		{
			errors.add("Wrong caseSignificanceId - expected " + ((DescriptionVersionBI)cv).isInitialCaseSignificant() + " but file has " + caseSignificanceId_);
		}
		
		throwErrors(errors);
	}
}
