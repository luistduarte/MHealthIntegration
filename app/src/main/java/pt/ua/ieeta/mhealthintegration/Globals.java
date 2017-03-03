package pt.ua.ieeta.mhealthintegration;

/**
 * Created by luis on 09-12-2016.
 */
public class Globals{
    private static Globals instance;

    // Global variable
    private String token;
    private String username;
    private String env = "omh";

    // Restrict the constructor from being instantiated
    private Globals(){}

    public void setData(String t){
        this.token=t;
    }
    public String getToken(){
        return this.token;
    }

    public String getUsername(){
        return this.username;
    }

    public void setUsername(String t){
        this.username=t;
    }

    public static synchronized Globals getInstance(){
        if(instance==null){
            instance=new Globals();
        }
        return instance;
    }

    public String getEnv() { return this.env; }
    public void setEnv(String t){
        this.env=t;
    }


}
