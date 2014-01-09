package gov.va.rf2.validator;

import java.io.File;

public class FileInfo
{
	private String fileType, contentType, contentSubType, countryNamespace, versionDate, extension;
	private File file;
	private boolean isUUIDFile = false;
	
	public FileInfo(File f)
	{
		file = f;
	}
	
	public void setIsUUIDFile(boolean isUUIDFile)
	{
		this.isUUIDFile = isUUIDFile;
	}
	
	public boolean getIsUUIDFile()
	{
		return isUUIDFile;
	}
	
	public File getFile()
	{
		return file;
	}
	
	public String getFileType()
	{
		return fileType;
	}
	
	public String getFileTypeCode()
	{
		String temp = fileType;
		if (temp == null)
		{
			return null;
		}
		if (temp.startsWith("x") || temp.startsWith("z"))
		{
			temp = temp.substring(1);
		}
		if (temp.length() > 3)
		{
			temp = temp.substring(0, 3);
		}
		return temp;
	}

	public void setFileType(String fileType)
	{
		if (this.fileType != null)
		{
			throw new UnsupportedOperationException("File type is already set");
		}
		this.fileType = fileType;
	}

	public String getContentType()
	{
		return contentType;
	}

	public void setContentType(String contentType)
	{
		if (this.contentType != null)
		{
			throw new UnsupportedOperationException("ContentType is already set");
		}
		this.contentType = contentType;
	}

	public String getContentSubType()
	{
		return contentSubType;
	}

	public void setContentSubType(String contentSubType)
	{
		if (this.contentSubType != null)
		{
			throw new UnsupportedOperationException("ContentSubType is already set");
		}
		this.contentSubType = contentSubType;
	}

	public String getCountryNamespace()
	{
		return countryNamespace;
	}

	public void setCountryNamespace(String countryNamespace)
	{
		if (this.countryNamespace != null)
		{
			throw new UnsupportedOperationException("Country|Namespace is already set");
		}
		this.countryNamespace = countryNamespace;
	}

	public String getVersionDate()
	{
		return versionDate;
	}

	public void setVersionDate(String versionDate)
	{
		if (this.versionDate != null)
		{
			throw new UnsupportedOperationException("VersionDate is already set");
		}
		this.versionDate = versionDate;
	}

	public String getExtension()
	{
		return extension;
	}

	public void setExtension(String extension)
	{
		if (this.extension != null)
		{
			throw new UnsupportedOperationException("Extension is already set");
		}
		this.extension = extension;
	}
}
