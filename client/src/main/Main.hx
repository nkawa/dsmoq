import pages.Definitions;

class Main{
  public static function main(){
    trace("hello world");
    new framework.Engine(Definitions.application(), DashBoard).start();
  }
}
