pushd
cd src 
call npm install
call npm run build
popd
node src/index.js %1 %2 %3 %4