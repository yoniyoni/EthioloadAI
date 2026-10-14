import { useState } from "react";
import { Link } from "wouter";
import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/contexts/auth-context";
import { api } from "@/lib/api";
import { CompanyTeam } from "@/components/company-team";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { statusHelp, statusLabel, type Company } from "@/lib/carrier-companies";

type Driver = {id:number;fullName:string;status:string;availability:string};
type Vehicle = {id:number;plateNumber:string;approvalStatus:string;availabilityStatus:string};
export default function CompanyWorkspace({id}:{id:string}) {
  const {memberships} = useAuth();
  const membership=memberships.find(m=>m.carrierCompanyId===Number(id));
  const [error,setError]=useState("");
  const [busy,setBusy]=useState(false);
  const company=useQuery({queryKey:["workspace-company",id],queryFn:()=>api.get<Company>(`/carrier-companies/${id}`),refetchInterval:query=>query.state.data?.verificationStatus==="PENDING"?3000:false});
  const approved=company.data?.verificationStatus==="APPROVED" && !!membership;
  const base=`/carrier-companies/${id}/fleet`;
  const drivers=useQuery({queryKey:["company-drivers",id],queryFn:()=>api.get<{content:Driver[]}>(`${base}/drivers?size=100`),enabled:approved});
  const vehicles=useQuery({queryKey:["company-vehicles",id],queryFn:()=>api.get<{content:Vehicle[]}>(`${base}/vehicles?size=100`),enabled:approved});
  const canManage=["CARRIER_OWNER","FLEET_MANAGER"].includes(membership?.role ?? "");
  async function submit(e:React.FormEvent<HTMLFormElement>,path:string,body:(f:FormData)=>object){
    e.preventDefault();const form=e.currentTarget;setBusy(true);setError("");
    try{await api.post(base+path,body(new FormData(form)));form.reset();await Promise.all([drivers.refetch(),vehicles.refetch()]);}
    catch(e){setError(e instanceof Error?e.message:"Unable to update fleet.");}finally{setBusy(false);}
  }
  if(company.isLoading)return <p>Loading company…</p>;
  if(company.error)return <p role="alert">{company.error.message}</p>;
  return <main className="mx-auto max-w-5xl p-6 space-y-6">
    <h1 className="text-3xl font-bold">{company.data?.companyName}</h1>
    <p>{membership?.role.replaceAll("_"," ") || "Company access unavailable"}</p>
    <p>{statusLabel[company.data?.verificationStatus ?? "DRAFT"]}</p>
    {company.data?.reviewMessage && <p>{company.data.reviewMessage}</p>}
    {membership?.role === "CARRIER_OWNER" && <Link href={`/fleet/companies/${id}`}>Manage company application</Link>}
    {!approved ? <p>{statusHelp[company.data?.verificationStatus ?? "DRAFT"]}</p> : <>
      <nav className="flex gap-4"><Link href={`/carrier-offers/company/${id}`}>Carrier offers</Link><Link href={`/carrier-dispatch/company/${id}`}>Dispatch</Link><Link href={`/carrier/${id}/settlements`}>Settlements</Link></nav>
      {(error || drivers.error || vehicles.error) && <p role="alert">{error || (drivers.error || vehicles.error)?.message}</p>}
      <h2 className="text-xl font-semibold">Drivers</h2>
      {drivers.data?.content.map(d=><p key={d.id}>{d.fullName} · {d.status} · {d.availability}</p>)}
      {canManage && <form className="flex gap-3" onSubmit={e=>void submit(e,"/drivers",f=>({driverUserId:Number(f.get("driverUserId"))}))}><Input aria-label="Driver account ID" name="driverUserId" type="number" min="1" required placeholder="Driver account ID"/><Button disabled={busy}>Add driver</Button></form>}
      <h2 className="text-xl font-semibold">Vehicles</h2>
      {vehicles.data?.content.map(v=><p key={v.id}>{v.plateNumber} · {v.approvalStatus} · {v.availabilityStatus}</p>)}
      {canManage && <>
        <form className="grid gap-3" onSubmit={e=>void submit(e,"/vehicles",f=>({plateNumber:f.get("plateNumber"),truckType:f.get("truckType"),capacity:Number(f.get("capacity")),currentCity:f.get("currentCity")}))}>
          <Input aria-label="Plate number" name="plateNumber" required placeholder="Plate number"/><Input aria-label="Truck type" name="truckType" required placeholder="Truck type"/><Input aria-label="Capacity" name="capacity" type="number" min="0.1" step="0.1" required placeholder="Capacity"/><Input aria-label="Current city" name="currentCity" placeholder="Current city"/><Button disabled={busy}>Register vehicle</Button>
        </form>
        <form className="flex gap-3" onSubmit={e=>void submit(e,"/assignments",f=>({carrierDriverId:Number(f.get("driver")),vehicleId:Number(f.get("vehicle"))}))}>
          <select name="driver" aria-label="Driver" required><option value="">Select driver</option>{drivers.data?.content.map(d=><option value={d.id} key={d.id}>{d.fullName}</option>)}</select>
          <select name="vehicle" aria-label="Vehicle" required><option value="">Select vehicle</option>{vehicles.data?.content.map(v=><option value={v.id} key={v.id}>{v.plateNumber}</option>)}</select><Button disabled={busy}>Assign vehicle</Button>
        </form>
      </>}
      {membership?.role==="CARRIER_OWNER" && <CompanyTeam companyId={Number(id)}/>}
    </>}
  </main>;
}
