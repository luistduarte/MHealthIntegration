package pt.ua.ieeta.mhealthintegration;

/**
 * Created by luis on 09-12-2016.
 */
public class Globals{
    private static Globals instance;

    // Global variable
    private String token;

    // Restrict the constructor from being instantiated
    private Globals(){}

    public void setData(String t){
        this.token=t;
    }
    public String getToken(){
        return this.token;
    }

    public static synchronized Globals getInstance(){
        if(instance==null){
            instance=new Globals();
        }
        return instance;
    }
}
