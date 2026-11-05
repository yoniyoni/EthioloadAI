if (-not ('EthioloadAI.DevProcessJob' -as [type])) {
    $jobType = @'
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;
using Microsoft.Win32.SafeHandles;

namespace EthioloadAI
{
    public sealed class DevProcessJob : IDisposable
    {
        private const uint JobObjectExtendedLimitInformation = 9;
        private const uint JobObjectLimitKillOnJobClose = 0x2000;
        private const uint ProcessTerminate = 0x0001;
        private const uint ProcessSetQuota = 0x0100;
        private IntPtr handle;

        [StructLayout(LayoutKind.Sequential)]
        private struct BasicLimitInformation
        {
            public long PerProcessUserTimeLimit;
            public long PerJobUserTimeLimit;
            public uint LimitFlags;
            public UIntPtr MinimumWorkingSetSize;
            public UIntPtr MaximumWorkingSetSize;
            public uint ActiveProcessLimit;
            public UIntPtr Affinity;
            public uint PriorityClass;
            public uint SchedulingClass;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct IoCounters
        {
            public ulong ReadOperationCount;
            public ulong WriteOperationCount;
            public ulong OtherOperationCount;
            public ulong ReadTransferCount;
            public ulong WriteTransferCount;
            public ulong OtherTransferCount;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct ExtendedLimitInformation
        {
            public BasicLimitInformation BasicLimitInformation;
            public IoCounters IoInfo;
            public UIntPtr ProcessMemoryLimit;
            public UIntPtr JobMemoryLimit;
            public UIntPtr PeakProcessMemoryUsed;
            public UIntPtr PeakJobMemoryUsed;
        }

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern IntPtr CreateJobObject(IntPtr attributes, string name);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool SetInformationJobObject(
            IntPtr job, uint informationClass, IntPtr information, uint informationLength);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern IntPtr OpenProcess(uint access, bool inheritHandle, int processId);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool AssignProcessToJobObject(IntPtr job, IntPtr process);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool TerminateJobObject(IntPtr job, uint exitCode);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool CloseHandle(IntPtr handle);

        public DevProcessJob()
        {
            handle = CreateJobObject(IntPtr.Zero, null);
            if (handle == IntPtr.Zero)
                throw new Win32Exception(Marshal.GetLastWin32Error());

            var limits = new ExtendedLimitInformation();
            limits.BasicLimitInformation.LimitFlags = JobObjectLimitKillOnJobClose;
            IntPtr buffer = Marshal.AllocHGlobal(Marshal.SizeOf(typeof(ExtendedLimitInformation)));
            try
            {
                Marshal.StructureToPtr(limits, buffer, false);
                if (!SetInformationJobObject(
                    handle, JobObjectExtendedLimitInformation, buffer,
                    (uint)Marshal.SizeOf(typeof(ExtendedLimitInformation))))
                    throw new Win32Exception(Marshal.GetLastWin32Error());
            }
            catch
            {
                Dispose();
                throw;
            }
            finally
            {
                Marshal.FreeHGlobal(buffer);
            }
        }

        public void Assign(int processId)
        {
            IntPtr process = OpenProcess(ProcessTerminate | ProcessSetQuota, false, processId);
            if (process == IntPtr.Zero)
                throw new Win32Exception(Marshal.GetLastWin32Error());
            try
            {
                if (!AssignProcessToJobObject(handle, process))
                    throw new Win32Exception(Marshal.GetLastWin32Error());
            }
            finally
            {
                CloseHandle(process);
            }
        }

        public void Terminate(uint exitCode)
        {
            if (handle != IntPtr.Zero && !TerminateJobObject(handle, exitCode))
                throw new Win32Exception(Marshal.GetLastWin32Error());
        }

        public void Dispose()
        {
            if (handle != IntPtr.Zero)
            {
                CloseHandle(handle);
                handle = IntPtr.Zero;
            }
        }
    }
}
'@
    Add-Type -TypeDefinition $jobType -Language CSharp -ErrorAction Stop
}

function New-DevProcessJob {
    return [EthioloadAI.DevProcessJob]::new()
}
