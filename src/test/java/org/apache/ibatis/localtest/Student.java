package org.apache.ibatis.localtest;

public class Student {

    private Integer SId;
    private String Sname;
    private Integer Sage;
    private String Ssex;

    public Student(){}

    public Student(Integer SId, String sname, Integer sage, String ssex) {
        this.SId = SId;
        Sname = sname;
        Sage = sage;
        Ssex = ssex;
    }

    public Integer getSId() {
        return SId;
    }

    public void setSId(Integer SId) {
        this.SId = SId;
    }

    public String getSname() {
        return Sname;
    }

    public void setSname(String sname) {
        Sname = sname;
    }

    public Integer getSage() {
        return Sage;
    }

    public void setSage(Integer sage) {
        Sage = sage;
    }

    public String getSsex() {
        return Ssex;
    }

    public void setSsex(String ssex) {
        Ssex = ssex;
    }

    @Override
    public String toString() {
        return "Student{" +
                "SId='" + SId + '\'' +
                ", Sname='" + Sname + '\'' +
                ", Sage='" + Sage + '\'' +
                ", Ssex='" + Ssex + '\'' +
                '}';
    }
}
