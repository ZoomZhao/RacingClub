// Offline hint generator. Searches real ports and the printed jump sequence;
// never participates in the App's independent success check.
#include <algorithm>
#include <array>
#include <cstdint>
#include <iostream>
#include <set>
#include <vector>
using namespace std;
struct Edge {int a,b,jump;};
struct Tile {int id,x,y,r;uint32_t cells;vector<int> ports;};
vector<Edge> paths[11];vector<Tile> tiles;vector<int> at[99];
int placed[11],seq[100],n,required,gate[99],startGate;long long visits;
vector<array<int,3>> route;set<array<int,3>> active;
int outsideCell(const Tile&t,int port) {
 int q=t.ports[port],x=q%11,y=q/11;
 // Ports are midpoints of cell edges, in half-cell coordinates.
 if(x%2==0){int cx=x/2+(x==t.x*2?-1:0),cy=y/2;return cx<0||cx>=5?-1:cy*5+cx;}
 int cy=y/2+(y==t.y*2?-1:0),cx=x/2;return cy<0||cy>=4?-1:cy*5+cx;
}
bool compatible(uint32_t occupied) {
 for(int id=1;id<=10;id++)if(placed[id]>=0){const Tile&t=tiles[placed[id]];
  for(int p=0;p<(int)t.ports.size();p++){
   int cell=outsideCell(t,p),q=t.ports[p];
   if(cell<0){if(!gate[q])return false;continue;}
   bool possible=false;
   for(int ti:at[q]){const Tile&o=tiles[ti];if(o.id==id||!(required&(1<<o.id)))continue;
    if(placed[o.id]==ti){possible=true;break;}
    if(placed[o.id]<0&&!(o.cells&occupied)){possible=true;break;}
   }
   if(!possible)return false;
  }
 }
 return true;
}
bool dfs(int ti,int entry,int index,int used,uint32_t occupied) {
 ++visits;
 if(visits%10000000==0)cerr<<"nodes "<<visits<<" progress "<<index<<'/'<<n<<'\n';
 array<int,3> state{ti,entry,index};if(!active.insert(state).second)return false;
 const Tile&t=tiles[ti];
 for(auto e:paths[t.id]){
  if(e.a!=entry&&e.b!=entry)continue;
  if(e.jump&&(index>=n||seq[index]!=e.jump))continue;
  int nextIndex=index+(e.jump!=0),out=e.a==entry?e.b:e.a,q=t.ports[out];
  route.push_back({t.id,entry,out});
  if(gate[q]){
   if(nextIndex==n&&used==required){cout<<"{\"start\":\""<<char('A'+startGate-1)<<"\",\"finish\":\""<<char('A'+gate[q]-1)<<"\",\"solution\":[";bool first=true;for(int id=1;id<=10;id++)if(placed[id]>=0){auto p=tiles[placed[id]];if(!first)cout<<',';first=false;cout<<"{\"id\":"<<id<<",\"x\":"<<p.x<<",\"y\":"<<p.y<<",\"r\":"<<p.r<<'}';}cout<<"],\"nodes\":"<<visits<<"}\n";return true;}
  }else for(int nt:at[q]){
   const Tile&o=tiles[nt];if(o.id==t.id||!(required&(1<<o.id)))continue;
   int en=find(o.ports.begin(),o.ports.end(),q)-o.ports.begin();
   if(placed[o.id]==nt){if(dfs(nt,en,nextIndex,used,occupied))return true;}
   else if(placed[o.id]<0&&!(o.cells&occupied)){
    placed[o.id]=nt;
    // A new tile changes the search state, so cycles of older layouts are not reused.
    auto prior=std::move(active);active.clear();
    if(compatible(occupied|o.cells)&&dfs(nt,en,nextIndex,used|(1<<o.id),occupied|o.cells))return true;
    active=std::move(prior);placed[o.id]=-1;
   }
  }
  route.pop_back();
 }
 active.erase(state);return false;
}
int main(){
 vector<pair<int,int>> ports[11]={ {},{{0,1},{4,1}},{{1,0},{3,2}},{{1,2},{3,0}},{{1,0},{3,0}},{{0,1},{3,0}},{{0,1},{3,2}},{{0,1},{1,0},{3,0},{3,2}},{{1,0},{1,2},{4,1}},{{1,2},{1,0},{3,0}},{{1,0},{1,2},{3,2}} };
 for(int id=1;id<=6;id++)paths[id]={{0,1,id}};
 paths[7]={{0,1,0},{2,3,7}};paths[8]={{0,1,8},{0,2,0}};paths[9]={{0,1,0},{0,2,9}};paths[10]={{0,1,0},{0,2,10}};
 gate[5]=1;gate[3*11+10]=2;gate[8*11+9]=3;gate[8*11+3]=4;gate[7*11]=5;gate[11]=6;
 cin>>n;for(int i=0;i<n;i++){cin>>seq[i];required|=1<<seq[i];}
 fill(begin(placed),end(placed),-1);
 for(int id=1;id<=10;id++)if(required&(1<<id))for(int r=0;r<(id<=3?2:4);r++)for(int y=0;y<4-(r%2);y++)for(int x=0;x<5-(!(r%2));x++){
  Tile t{id,x,y,r,0,{}};for(int yy=y;yy<y+(r%2?2:1);yy++)for(int xx=x;xx<x+(r%2?1:2);xx++)t.cells|=1<<(yy*5+xx);
  for(auto [a,b]:ports[id]){int xx=a,yy=b;if(r==1){xx=2-b;yy=a;}if(r==2){xx=4-a;yy=2-b;}if(r==3){xx=b;yy=4-a;}t.ports.push_back((y*2+yy)*11+x*2+xx);}
  for(int p:t.ports)at[p].push_back(tiles.size());tiles.push_back(t);
 }
 for(int q=0;q<99;q++)if(gate[q]){startGate=gate[q];for(int ti:at[q]){const Tile&t=tiles[ti];placed[t.id]=ti;int en=find(t.ports.begin(),t.ports.end(),q)-t.ports.begin();if(compatible(t.cells)&&dfs(ti,en,0,1<<t.id,t.cells))return 0;placed[t.id]=-1;active.clear();}}
 cerr<<"No solution after "<<visits<<" nodes\n";return 1;
}
