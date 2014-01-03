package gov.va.rf2.validator;

/**
 * Computes the check-digit used in an SCTID. The SCTID includes a check-digit.
 * The College of American Pathologists (CAP) has specified the use of the Verhoeff's
 * Dihedral Check as the algorithm to use for generating these check digits. A further
 * explanation of this can be found in the SNOMED Technical Reference Guide.
 * 
 * @see "Appendix F. Check-digit computation - SNOMED Clinical Terms Technical Reference Guide, July 2006 Release"
 */
public class VerhoeffDihedralCheck
{
	private static final int[][] FnF = new int[10][10];
	static
	{
		FnF[0] = new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
		FnF[1] = new int[] { 1, 5, 7, 6, 2, 8, 3, 0, 9, 4 };
		for (int i = 2; i < 8; i++)
		{
			for (int j = 0; j < 10; j++)
			{
				FnF[i][j] = FnF[i - 1][FnF[1][j]];
			}
		}
	}
	private static final int[][] Dihedral = new int[][] { { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 },
		{ 1, 2, 3, 4, 0, 6, 7, 8, 9, 5 },
		{ 2, 3, 4, 0, 1, 7, 8, 9, 5, 6 },
		{ 3, 4, 0, 1, 2, 8, 9, 5, 6, 7 },
		{ 4, 0, 1, 2, 3, 9, 5, 6, 7, 8 },
		{ 5, 9, 8, 7, 6, 0, 4, 3, 2, 1 },
		{ 6, 5, 9, 8, 7, 1, 0, 4, 3, 2 },
		{ 7, 6, 5, 9, 8, 2, 1, 0, 4, 3 },
		{ 8, 7, 6, 5, 9, 3, 2, 1, 0, 4 },
		{ 9, 8, 7, 6, 5, 4, 3, 2, 1, 0 }};

	private static final int[] InverseD5 = new int[] { 0, 4, 3, 2, 1, 5, 6, 7, 8, 9 };

	public static void validateCheckDigit(String sctid) throws Exception
	{
		String idValue = sctid.substring(0, sctid.length() - 1);
		int check = 0;
		for (int i = idValue.length() - 1; i >= 0; i--)
		{
			int fnfrow = (idValue.length() - i) % 8;
			int fnfcol = (new Integer(idValue.substring(i, i + 1))).intValue();
			check = Dihedral[check][FnF[fnfrow][fnfcol]];
		}
		String computed = (new Integer(InverseD5[check])).toString();
		if (!computed.equals(sctid.substring(sctid.length() - 1, sctid.length())))
		{
			throw new Exception("SCTID check digit should be '" + computed + "'");
		}
	}
}
