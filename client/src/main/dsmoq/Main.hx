package dsmoq;

import dsmoq.pages.Definitions;

class Main{
  public static function main(){
    new dsmoq.framework.Engine(Definitions.application()).start();
  }
}
