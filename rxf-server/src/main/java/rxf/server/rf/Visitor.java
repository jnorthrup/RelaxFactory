package rxf.server.rf;

import com.google.gson.annotations.SerializedName;

import java.util.Date;

/**
 * User: jim
 * Date: 4/15/12
 * Time: 9:57 PM
 */

public class Visitor{
  @SerializedName("_id")
  private String id;
  @SerializedName("_rev")
  private String version;
  private Date   creation;

  public static CouchLocator<Visitor> createLocator(){
    return new CouchLocator<Visitor>(){
      @Override
      public Class<Visitor> getDomainType(){
        return Visitor.class;
      }

      @Override
      public String getId(Visitor domainObject){
        return domainObject.getId();
      }

      @Override
      public Object getVersion(Visitor domainObject){
        return domainObject.getVersion();
      }
    };
  }

  public static Visitor createSession(){
    Visitor roSession=new Visitor();
    roSession.setCreation(new Date());
    return roSession;
  }

  public String getId(){
    return id;
  }

  public void setId(String id){
    this.id=id;
  }

  public Date getCreation(){
    return creation;
  }

  public void setCreation(Date creation){
    this.creation=creation;
  }

  public String getVersion(){
    return version;
  }

  public void setVersion(String version){
    this.version=version;
  }

  @Override
  public String toString(){
    return "Visitor{"+"id='"+id+'\''+", version='"+version+'\''+", creation="+creation+'}';
  }
}
