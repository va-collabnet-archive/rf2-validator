package gov.va.rf2.validator.rowData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.UUID;
import org.ihtsdo.tk.Ts;
import org.ihtsdo.tk.api.ComponentChronicleBI;
import org.ihtsdo.tk.api.ComponentVersionBI;
import org.ihtsdo.tk.api.concept.ConceptChronicleBI;
import org.ihtsdo.tk.api.conceptattribute.ConceptAttributeVersionBI;
import org.ihtsdo.tk.api.id.IdBI;
import org.ihtsdo.tk.binding.snomed.SnomedMetadataRf2;
import org.ihtsdo.tk.binding.snomed.TermAux;

public class ValidatorBase
{
	protected HashMap<Long, UUID> sctToUUIDMap_;
	protected static final UUID SCTAuthority = TermAux.SCT_ID_AUTHORITY.getUuids()[0];

	@SuppressWarnings("rawtypes")
	protected ConceptAttributeVersionBI getNewest(ConceptChronicleBI c) throws IOException
	{
		ArrayList<ConceptAttributeVersionBI> cabs = new ArrayList<>();
		cabs.addAll(c.getConceptAttributes().getVersions());
		
		//TODO Make sure this sorts the right direction
		Collections.sort(cabs, new Comparator<ConceptAttributeVersionBI>()
		{

			@Override
			public int compare(ConceptAttributeVersionBI o1, ConceptAttributeVersionBI o2)
			{
				try
				{
					return -1 * Long.compare(o1.getPosition().getTime(), o2.getPosition().getTime());
				}
				catch (IOException e)
				{
					throw new RuntimeException(e);
				}
			}
		});
		
		return cabs.get(0);
	}
	
	protected ComponentVersionBI getNewest(ComponentChronicleBI<?> cc) throws IOException
	{
		ArrayList<ComponentVersionBI> cabs = new ArrayList<>();
		cabs.addAll(cc.getVersions());
		
		//TODO Make sure this sorts the right direction
		Collections.sort(cabs, new Comparator<ComponentVersionBI>()
		{

			@Override
			public int compare(ComponentVersionBI o1, ComponentVersionBI o2)
			{
				try
				{
					return -1 * Long.compare(o1.getPosition().getTime(), o2.getPosition().getTime());
				}
				catch (IOException e)
				{
					throw new RuntimeException(e);
				}
			}
		});
		
		return cabs.get(0);
	}
	
	protected void throwErrors(ArrayList<String> errors) throws Exception
	{
		if (errors.size() > 0)
		{
			StringBuilder sb = new StringBuilder();
			for (String s : errors)
			{
				sb.append(s + "; "); 
			}
			sb.setLength(sb.length() - 2);
			throw new Exception(sb.toString());
		}
	}
	
	protected ConceptAttributeVersionBI<?> lookupConceptByUUID(UUID uuid) throws Exception
	{
		ConceptChronicleBI c = Ts.get().getConcept(uuid);
		if (!Ts.get().hasUuid(uuid) || c == null || c.getPrimUuid() == null || c.getConceptAttributes() == null || c.getConceptAttributes().getVersions().size() < 1)
		{
			throw new Exception("Couldn't find the concept with the ID " + uuid + " in the DB");
		}
		return getNewest(c);
	}
	
	

	protected ConceptAttributeVersionBI<?> lookupConceptBySCTID(long sctid) throws Exception
	{
		int nid = Ts.get().getNidFromAlternateId(SCTAuthority, sctid + "");
		// Seriously. WTF is the difference between getConceptforNid(nid) and getConcept(nid). This API....
		ConceptChronicleBI c = Ts.get().getConceptForNid(nid);
		if (c == null || c.getPrimUuid() == null || c.getConceptAttributes() == null || c.getConceptAttributes().getVersions().size() < 1)
		{
			// The exported concept didn't have a preexisting SCTID - it was probably generated. Check the map file.
			UUID uuid = sctToUUIDMap_.get(sctid);
			if (uuid != null && Ts.get().hasUuid(uuid))
			{
				return lookupConceptByUUID(uuid);
			}
			else
			{
				throw new Exception("Couldn't find the concept with the ID " + sctid + " in the DB");
			}
		}
		else
		{
			return getNewest(c);
		}
	}
	
	protected ComponentVersionBI lookupComponentBySCTID(long sctid) throws Exception
	{
		int nid = Ts.get().getNidFromAlternateId(SCTAuthority, sctid + "");
		ComponentChronicleBI<?> c = Ts.get().getComponent(nid);
		if (c == null || c.getPrimUuid() == null || c.getVersions() == null || c.getVersions().size() < 1)
		{
			// The exported concept didn't have a preexisting SCTID - it was probably generated. Check the map file.
			UUID uuid = sctToUUIDMap_.get(sctid);
			if (uuid != null && Ts.get().hasUuid(uuid))
			{
				return lookupComponentByUUID(uuid);
			}
			else
			{
				throw new Exception("Couldn't find the component with the ID " + sctid + " in the DB");
			}
		}
		else
		{
			return getNewest(c);
		}
	}
	
	protected ComponentVersionBI lookupComponentByUUID(UUID id) throws Exception
	{
		ComponentChronicleBI<?> cc = Ts.get().getComponent(id);
		
		if (cc == null || cc.getPrimUuid() == null || cc.getVersions() == null || cc.getVersions().size() < 1)
		{
			throw new Exception("Couldn't find the component with the id " + id);
		}
		
		return getNewest(cc);
	}
	
	protected void checkStatus(ComponentVersionBI wbItem, boolean rowStatus) throws Exception
	{
		UUID wbStatusUUID = Ts.get().getComponent(wbItem.getStatusNid()).getPrimUuid();
		boolean wbActiveStatus = (wbStatusUUID.equals(SnomedMetadataRf2.ACTIVE_VALUE_RF2.getUuids()[0])
				|| wbStatusUUID.equals(SnomedMetadataRf2.PENDING_MOVE_RF2.getUuids()[0]) || wbStatusUUID.equals(SnomedMetadataRf2.CONCEPT_NON_CURRENT_RF2.getUuids()[0]));
		if (rowStatus != wbActiveStatus)
		{
			throw new Exception("Wrong status - expected " + wbActiveStatus + " but found " + rowStatus);
		}
	}
	
	protected void checkModule(ComponentVersionBI wbItem, long rowModule) throws Exception
	{
		
		Collection<? extends IdBI> altIds = getNewest(Ts.get().getConcept(wbItem.getModuleNid())).getAdditionalIds();

		if (altIds != null)
		{
			for (IdBI id : altIds)
			{
				if (id.getAuthorityNid() == TermAux.SCT_ID_AUTHORITY.getLenient().getNid())
				{
					//TODO not sure if multiple SCTIDs are allowed, if so, need a loop
					if (!(id.getDenotation().toString().equals(rowModule + "")))
					{
						throw new Exception("Expected moduleID of " + id.getDenotation() + " but found " + rowModule);
					}
					else
					{
						//correct value...
						return;
					}
				}
			}
		}

		// perhaps, didn't have the ID because it was generated... check the map file.
		UUID dbModule = getNewest(Ts.get().getConcept(wbItem.getModuleNid())).getPrimUuid();
		UUID writtenModule = sctToUUIDMap_.get(rowModule);
		if (!dbModule.equals(writtenModule))
		{
			if (writtenModule == null)
			{
				// Wow... seem to have the wrong module. Do another lookup to improve the error message...
				// Since the SCTID didn't exist in the map file, and it didn't match the module we looked up in the DB.. look it up directly.
				ConceptChronicleBI writtenModuleConcept = Ts.get().getConceptForNid(Ts.get().getNidFromAlternateId(SCTAuthority, rowModule + ""));
				throw new Exception("Expected moduleID of " + dbModule + " (SCTID not in DB) but found " + rowModule + " (" + writtenModuleConcept.getPrimUuid() + ")");
			}
			else
			{
				throw new Exception("Expected moduleID of " + dbModule + " (SCTID not in DB) but found " + rowModule + " (" + writtenModule + ")");
			}
		}
	}
}
