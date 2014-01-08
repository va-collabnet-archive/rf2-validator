package gov.va.rf2.validator.rowData;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;
import org.ihtsdo.tk.Ts;
import org.ihtsdo.tk.api.description.DescriptionVersionBI;

public class Description extends ValidatorBase
{
	long id_;
	Date effectiveTime_, expectedEffectiveTime_;
	boolean active_;
	long moduleId_;
	long conceptId_;
	String languageCode_;
	long typeId_;
	String term_;
	long caseSignificanceId_;
	
	public Description(Object[] data, Date expectedEffectiveTime, HashMap<Long, UUID> sctToUUIDMap)
	{
		id_ = (long)data[0];
		effectiveTime_ = (Date)data[1];
		active_ = (boolean)data[2];
		moduleId_ = (long)data[3];
		conceptId_ = (long)data[4];
		languageCode_ = (String)data[5];
		typeId_ = (long)data[6];
		term_ = (String)data[7];
		caseSignificanceId_ = (long)data[8];
		expectedEffectiveTime_ = expectedEffectiveTime;
		sctToUUIDMap_ = sctToUUIDMap;
	}
	
	@SuppressWarnings("rawtypes")
	public void validate() throws Exception
	{
		ArrayList<String> errors = new ArrayList<>();

		DescriptionVersionBI dv = (DescriptionVersionBI)lookupComponentBySCTID(id_);
		
		if (expectedEffectiveTime_.getTime() != effectiveTime_.getTime())
		{
			errors.add("Wrong time - expected " + expectedEffectiveTime_.toString() + " but file has " + effectiveTime_.toString());
		}

		try
		{
			checkStatus(dv, active_);
		}
		catch (Exception e)
		{
			errors.add(e.getMessage());
		}
		
		try
		{
			checkModule(dv, moduleId_);
		}
		catch (Exception e)
		{
			errors.add(e.getMessage());
		}
		
		lookupConceptBySCTID(conceptId_);
		
		//languageCode
		if (!dv.getLang().equals(languageCode_))
		{
			errors.add("Wrong languageCode - expected " + dv.getLang() + " but file has " + languageCode_);
		}
		
		//typeId
		if (!lookupConceptBySCTID(typeId_).getPrimUuid().equals(Ts.get().getConceptForNid(dv.getTypeNid()).getPrimUuid()))
		{
			errors.add("Wrong typeId - expected an SCTID for " + Ts.get().getConceptForNid(dv.getTypeNid()).getPrimUuid() + " but file has " + typeId_);
		}
		
		//term
		if (!dv.getText().equals(term_))
		{
			errors.add("Wrong term - expected " + dv.getText() + " but file has " + term_);
		}
		
		//caseSig
		long expectedCase = (dv.isInitialCaseSignificant() ? 900000000000017005l : 900000000000448009l);
		if (caseSignificanceId_ != expectedCase)
		{
			errors.add("Wrong caseSignificanceId - expected " + expectedCase + " but file has " + caseSignificanceId_);
		}
		
		throwErrors(errors);
	}
}
