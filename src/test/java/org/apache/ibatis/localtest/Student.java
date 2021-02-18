package org.apache.ibatis.localtest;

public class Student {

    private Integer sId;
    private String sName;
    private Integer sAge;
    private String sSex;

    public Student(){}

    public Student(Integer SId, String sname, Integer sage, String ssex) {
        this.sId = SId;
        sName = sname;
        sAge = sage;
        sSex = ssex;
    }

    public Integer getSId() {
        return sId;
    }

    public void setSId(Integer SId) {
        this.sId = SId;
    }

    public String getSname() {
        return sName;
    }

    public void setSname(String sname) {
        sName = sname;
    }

    public Integer getSage() {
        return sAge;
    }

    public void setSage(Integer sage) {
        sAge = sage;
    }

    public String getSsex() {
        return sSex;
    }

    public void setSsex(String ssex) {
        sSex = ssex;
    }

    @Override
    public String toString() {
        return "Student{" +
                "SId='" + sId + '\'' +
                ", Sname='" + sName + '\'' +
                ", Sage='" + sAge + '\'' +
                ", Ssex='" + sSex + '\'' +
                '}';
    }
}
