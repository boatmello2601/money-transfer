#!/bin/sh
for i in $(seq 1 15); do
  OUTPUT=$(echo "SET AUTHREC PROFILE('TRANSFER.COMPLETED') OBJTYPE(QUEUE) PRINCIPAL('app') AUTHADD(PUT,GET,BROWSE,INQ,DSP)" | docker exec -i bank-ibmmq runmqsc QM1 2>&1)
  echo "$OUTPUT"
  if echo "$OUTPUT" | grep -q "AMQ8871E"; then
    echo "รอบที่ $i: user ยังไม่พร้อม รอ 3 วินาทีแล้วลองใหม่..."
    sleep 3
  else
    echo "ให้สิทธิ์สำเร็จในรอบที่ $i"
    exit 0
  fi
done
echo "ลองครบ 15 รอบแล้วยังไม่สำเร็จ"
exit 1