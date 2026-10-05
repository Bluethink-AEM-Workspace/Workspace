package com.workspace.core.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AlgoliaRecord {

    @JsonProperty("objectID")
    private String objectID;
    private String title;
    private String description;
    private String path;
    private String contentType;
    private String[] tags;
    private long publishDate;
    private String image;

    // Default Constructor
    public AlgoliaRecord() {}

    // Getters and Setters
    public String getObjectID() { 
        return objectID; 
    }
    public void setObjectID(String objectID) { 
        this.objectID = objectID; 
    }

    public String getTitle() { 
        return title; 
    }
    public void setTitle(String title) { 
        this.title = title; 
    }

    public String getDescription() { 
        return description; 
    }
    public void setDescription(String description) { 
        this.description = description; 
    }

    public String getPath() { 
        return path; 
    }
    public void setPath(String path) { 
        this.path = path; 
    }

    public String getContentType() { 
        return contentType; 
    }
    public void setContentType(String contentType) { 
        this.contentType = contentType; 
    }

    public String[] getTags() { 
        return tags; 
    }
    public void setTags(String[] tags) { 
        this.tags = tags; 
    }

    public long getPublishDate() { 
        return publishDate; 
    }
    public void setPublishDate(long publishDate) { 
        this.publishDate = publishDate; 
    }

    public String getImage() { 
        return image; 
    }
    public void setImage(String image) { 
        this.image = image; 
    }
}